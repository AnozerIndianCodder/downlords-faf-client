package com.faforever.client.remote;

import com.faforever.client.game.Faction;
import com.faforever.client.i18n.I18n;
import com.faforever.client.legacy.FactionDeserializer;
import com.faforever.client.legacy.ServerMessageSerializer;
import com.faforever.client.legacy.UidService;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.ForgedAlliancePrefs;
import com.faforever.client.preferences.LoginPrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rankedmatch.MatchmakerMessage;
import com.faforever.client.rankedmatch.SearchRanked1V1ClientMessage;
import com.faforever.client.rankedmatch.StopSearchRanked1V1ClientMessage;
import com.faforever.client.remote.domain.ClientMessageType;
import com.faforever.client.remote.domain.FafServerMessage;
import com.faforever.client.remote.domain.FafServerMessageType;
import com.faforever.client.remote.domain.GameLaunchMessage;
import com.faforever.client.remote.domain.InitSessionMessage;
import com.faforever.client.remote.domain.LoginClientMessage;
import com.faforever.client.remote.domain.LoginMessage;
import com.faforever.client.remote.domain.MessageTarget;
import com.faforever.client.remote.domain.NoticeMessage;
import com.faforever.client.remote.domain.RatingRange;
import com.faforever.client.remote.domain.SessionMessage;
import com.faforever.client.remote.gson.ClientMessageTypeTypeAdapter;
import com.faforever.client.remote.gson.MessageTargetTypeAdapter;
import com.faforever.client.remote.gson.RatingRangeTypeAdapter;
import com.faforever.client.remote.gson.ServerMessageTypeTypeAdapter;
import com.faforever.client.remote.io.QDataInputStream;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FafServerAccessorImplTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final long TIMEOUT = 5000;
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
  private static final int GAME_PORT = 6112;
  private static final InetAddress LOOPBACK_ADDRESS = InetAddress.getLoopbackAddress();
  private static final Gson gson = new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .registerTypeAdapter(ClientMessageType.class, ClientMessageTypeTypeAdapter.INSTANCE)
      .registerTypeAdapter(FafServerMessageType.class, ServerMessageTypeTypeAdapter.INSTANCE)
      .registerTypeAdapter(MessageTarget.class, MessageTargetTypeAdapter.INSTANCE)
      .registerTypeAdapter(Faction.class, new FactionDeserializer())
      .registerTypeAdapter(RatingRange.class, RatingRangeTypeAdapter.INSTANCE)
      .create();

  @Rule
  public TemporaryFolder faDirectory = new TemporaryFolder();

  @Mock
  private PreferencesService preferencesService;
  @Mock
  private Preferences preferences;
  @Mock
  private UidService uidService;
  @Mock
  private ForgedAlliancePrefs forgedAlliancePrefs;
  @Mock
  private NotificationService notificationService;
  @Mock
  private I18n i18n;

  private FafServerAccessorImpl instance;
  private ServerSocket fafLobbyServerSocket;
  private Socket localToServerSocket;
  private ServerWriter serverToClientWriter;
  private boolean stopped;
  private BlockingQueue<String> messagesReceivedByFafServer;
  private CountDownLatch serverToClientReadyLatch;

  @Before
  public void setUp() throws Exception {
    serverToClientReadyLatch = new CountDownLatch(1);
    messagesReceivedByFafServer = new ArrayBlockingQueue<>(10);

    startFakeFafLobbyServer();

    instance = new FafServerAccessorImpl(preferencesService, uidService, notificationService, i18n, LOOPBACK_ADDRESS.getHostAddress(), fafLobbyServerSocket.getLocalPort());

    LoginPrefs loginPrefs = new LoginPrefs();
    loginPrefs.setUsername("junit");
    loginPrefs.setPassword("password");

    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferencesService.getFafDataDirectory()).thenReturn(faDirectory.getRoot().toPath());
    when(preferences.getForgedAlliance()).thenReturn(forgedAlliancePrefs);
    when(forgedAlliancePrefs.getPort()).thenReturn(GAME_PORT);
    when(preferences.getLogin()).thenReturn(loginPrefs);
    when(uidService.generate(any(), any())).thenReturn("encrypteduidstring");

    preferencesService.getPreferences().getLogin();
  }

  private void startFakeFafLobbyServer() throws IOException {
    fafLobbyServerSocket = new ServerSocket(0);
    logger.info("Fake server listening on " + fafLobbyServerSocket.getLocalPort());

    WaitForAsyncUtils.async(() -> {

      try (Socket socket = fafLobbyServerSocket.accept()) {
        localToServerSocket = socket;
        QDataInputStream qDataInputStream = new QDataInputStream(new DataInputStream(socket.getInputStream()));
        serverToClientWriter = new ServerWriter(socket.getOutputStream());
        serverToClientWriter.registerMessageSerializer(new ServerMessageSerializer(), FafServerMessage.class);

        serverToClientReadyLatch.countDown();

        while (!stopped) {
          qDataInputStream.readInt();
          String json = qDataInputStream.readQString();

          messagesReceivedByFafServer.add(json);
        }
      } catch (IOException e) {
        System.out.println("Closing fake FAF lobby server: " + e.getMessage());
        throw new RuntimeException(e);
      }
    });
  }

  @After
  public void tearDown() {
    IOUtils.closeQuietly(fafLobbyServerSocket);
    IOUtils.closeQuietly(localToServerSocket);
  }

  @Test
  public void testConnectAndLogIn() throws Exception {
    int playerUid = 123;
    String username = "JunitUser";
    String password = "JunitPassword";
    long sessionId = 456;

    CompletableFuture<LoginMessage> loginFuture = instance.connectAndLogIn(username, password).toCompletableFuture();

    String json = messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT);
    InitSessionMessage initSessionMessage = gson.fromJson(json, InitSessionMessage.class);

    assertThat(initSessionMessage.getCommand(), is(ClientMessageType.ASK_SESSION));

    SessionMessage sessionMessage = new SessionMessage();
    sessionMessage.setSession(sessionId);
    sendFromServer(sessionMessage);

    json = messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT);
    LoginClientMessage loginClientMessage = gson.fromJson(json, LoginClientMessage.class);

    assertThat(loginClientMessage.getCommand(), is(ClientMessageType.LOGIN));
    assertThat(loginClientMessage.getLogin(), is(username));
    assertThat(loginClientMessage.getPassword(), is(password));
    assertThat(loginClientMessage.getSession(), is(sessionId));
    assertThat(loginClientMessage.getUniqueId(), is("encrypteduidstring"));

    LoginMessage loginServerMessage = new LoginMessage();
    loginServerMessage.setId(playerUid);
    loginServerMessage.setLogin(username);

    sendFromServer(loginServerMessage);

    LoginMessage result = loginFuture.get(TIMEOUT, TIMEOUT_UNIT);

    assertThat(result.getMessageType(), is(FafServerMessageType.WELCOME));
    assertThat(result.getId(), is(playerUid));
    assertThat(result.getLogin(), is(username));
  }

  /**
   * Writes the specified message to the client as if it was sent by the FAF server.
   */
  private void sendFromServer(FafServerMessage fafServerMessage) throws InterruptedException {
    serverToClientReadyLatch.await();
    serverToClientWriter.write(fafServerMessage);
  }

  private void connectAndLogIn() throws Exception {
    CompletableFuture<LoginMessage> loginFuture = instance.connectAndLogIn("JUnit", "JUnitPassword").toCompletableFuture();

    assertNotNull(messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT));

    SessionMessage sessionMessage = new SessionMessage();
    sessionMessage.setSession(5678);
    sendFromServer(sessionMessage);

    assertNotNull(messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT));

    LoginMessage loginServerMessage = new LoginMessage();
    loginServerMessage.setId(123);
    loginServerMessage.setLogin("JUnitUser");

    sendFromServer(loginServerMessage);

    assertNotNull(loginFuture.get(TIMEOUT, TIMEOUT_UNIT));
  }

  @Test
  public void testRankedMatchNotification() throws Exception {
    connectAndLogIn();

    MatchmakerMessage matchmakerMessage = new MatchmakerMessage();
    matchmakerMessage.setQueues(singletonList(new MatchmakerMessage.MatchmakerQueue("ladder1v1", singletonList(new RatingRange(100, 200)), singletonList(new RatingRange(100, 200)))));

    CompletableFuture<MatchmakerMessage> serviceStateDoneFuture = new CompletableFuture<>();

    WaitForAsyncUtils.waitForAsyncFx(200, () -> instance.addOnMessageListener(
        MatchmakerMessage.class, serviceStateDoneFuture::complete
    ));

    sendFromServer(matchmakerMessage);

    MatchmakerMessage matchmakerServerMessage = serviceStateDoneFuture.get(TIMEOUT, TIMEOUT_UNIT);

    assertThat(matchmakerServerMessage.getQueues(), not(empty()));
  }


  @Test
  public void testOnNotice() throws Exception {
    connectAndLogIn();

    NoticeMessage noticeMessage = new NoticeMessage();
    noticeMessage.setText("foo bar");
    noticeMessage.setStyle("warning");

    when(i18n.get("messageFromServer")).thenReturn("Message from Server");

    sendFromServer(noticeMessage);

    ArgumentCaptor<ImmediateNotification> captor = ArgumentCaptor.forClass(ImmediateNotification.class);
    verify(notificationService, timeout(1000)).addNotification(captor.capture());

    ImmediateNotification notification = captor.getValue();
    assertThat(notification.getSeverity(), is(Severity.WARN));
    assertThat(notification.getText(), is("foo bar"));
    assertThat(notification.getTitle(), is("Message from Server"));
    verify(i18n).get("messageFromServer");
  }

  @Test
  public void startSearchRanked1v1WithAeon() throws Exception {
    connectAndLogIn();
    InetSocketAddress relayAddress = InetSocketAddress.createUnresolved("foobar", 1235);

    CompletableFuture<GameLaunchMessage> future = instance.startSearchRanked1v1(Faction.AEON).toCompletableFuture();

    String clientMessage = messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT);
    SearchRanked1V1ClientMessage searchRanked1v1Message = gson.fromJson(clientMessage, SearchRanked1V1ClientMessage.class);

    assertThat(searchRanked1v1Message, instanceOf(SearchRanked1V1ClientMessage.class));
    assertThat(searchRanked1v1Message.getFaction(), is(Faction.AEON));

    GameLaunchMessage gameLaunchMessage = new GameLaunchMessage();
    gameLaunchMessage.setUid(1234);
    sendFromServer(gameLaunchMessage);

    assertThat(future.get(TIMEOUT, TIMEOUT_UNIT).getUid(), is(gameLaunchMessage.getUid()));
  }

  @Test
  public void stopSearchingRanked1v1Match() throws Exception {
    connectAndLogIn();

    instance.stopSearchingRanked();

    String clientMessage = messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT);
    StopSearchRanked1V1ClientMessage stopSearchRanked1v1Message = gson.fromJson(clientMessage, StopSearchRanked1V1ClientMessage.class);
    assertThat(stopSearchRanked1v1Message, instanceOf(StopSearchRanked1V1ClientMessage.class));
    assertThat(stopSearchRanked1v1Message.getCommand(), is(ClientMessageType.GAME_MATCH_MAKING));
  }
}
