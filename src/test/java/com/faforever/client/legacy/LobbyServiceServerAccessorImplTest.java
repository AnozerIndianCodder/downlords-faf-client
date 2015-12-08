package com.faforever.client.legacy;

import com.faforever.client.game.Faction;
import com.faforever.client.legacy.domain.ClientMessageType;
import com.faforever.client.legacy.domain.FafServerMessage;
import com.faforever.client.legacy.domain.FafServerMessageType;
import com.faforever.client.legacy.domain.GameLaunchMessage;
import com.faforever.client.legacy.domain.GameTypeMessage;
import com.faforever.client.legacy.domain.InitSessionMessage;
import com.faforever.client.legacy.domain.LoginClientMessage;
import com.faforever.client.legacy.domain.LoginMessage;
import com.faforever.client.legacy.domain.MessageTarget;
import com.faforever.client.legacy.domain.SessionMessage;
import com.faforever.client.legacy.gson.ClientMessageTypeTypeAdapter;
import com.faforever.client.legacy.gson.MessageTargetTypeAdapter;
import com.faforever.client.legacy.gson.ServerMessageTypeTypeAdapter;
import com.faforever.client.legacy.io.QDataInputStream;
import com.faforever.client.legacy.writer.ServerWriter;
import com.faforever.client.preferences.ForgedAlliancePrefs;
import com.faforever.client.preferences.LoginPrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.rankedmatch.MatchmakerMessage;
import com.faforever.client.rankedmatch.SearchRanked1V1ClientMessage;
import com.faforever.client.rankedmatch.StopSearchRanked1V1ClientMessage;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.update.ClientUpdateService;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LobbyServiceServerAccessorImplTest extends AbstractPlainJavaFxTest {

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
  private ClientUpdateService clientUpdateService;

  private LobbyServerAccessorImpl instance;
  private LoginPrefs loginPrefs;
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

    instance = new LobbyServerAccessorImpl();
    instance.preferencesService = preferencesService;
    instance.uidService = uidService;
    instance.lobbyHost = LOOPBACK_ADDRESS.getHostAddress();
    instance.lobbyPort = fafLobbyServerSocket.getLocalPort();
    instance.clientUpdateService = clientUpdateService;

    loginPrefs = new LoginPrefs();
    loginPrefs.setUsername("junit");
    loginPrefs.setPassword("password");

    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferencesService.getFafDataDirectory()).thenReturn(faDirectory.getRoot().toPath());
    when(preferences.getForgedAlliance()).thenReturn(forgedAlliancePrefs);
    when(forgedAlliancePrefs.getPort()).thenReturn(GAME_PORT);
    when(preferences.getLogin()).thenReturn(loginPrefs);
    when(uidService.generate(any(), any())).thenReturn("encrypteduidstring");
    when(clientUpdateService.getCurrentVersion()).thenReturn(new ComparableVersion("1.0"));

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
          int blockSize = qDataInputStream.readInt();
          String json = qDataInputStream.readQString();

          if (blockSize > json.length() * 2) {
            // Username
            qDataInputStream.readQString();
            // Session ID
            qDataInputStream.readQString();
          }

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

    CompletableFuture<LoginMessage> loginFuture = instance.connectAndLogIn(username, password);

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
    assertThat(loginClientMessage.getVersion(), is("1.0"));
    assertThat(loginClientMessage.getUserAgent(), is("downlords-faf-client"));

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

  @Test
  public void testAddOnGameTypeInfoListener() throws Exception {
    connectAndLogIn();

    CompletableFuture<GameTypeMessage> gameTypeInfoFuture = new CompletableFuture<>();
    @SuppressWarnings("unchecked")
    Consumer<GameTypeMessage> listener = mock(Consumer.class);
    doAnswer(invocation -> {
      gameTypeInfoFuture.complete(invocation.getArgumentAt(0, GameTypeMessage.class));
      return null;
    }).when(listener).accept(any());

    instance.addOnMessageListener(GameTypeMessage.class, listener);

    String name = "test";
    String fullname = "Test game type";
    String description = "Game type description";
    String icon = "what";
    Boolean[] options = new Boolean[]{TRUE, FALSE, TRUE};

    GameTypeMessage gameTypeMessage = new GameTypeMessage();
    gameTypeMessage.setName(name);
    gameTypeMessage.setFullname(fullname);
    gameTypeMessage.setDesc(description);
    gameTypeMessage.setIcon(icon);
    gameTypeMessage.setOptions(options);

    sendFromServer(gameTypeMessage);

    GameTypeMessage result = gameTypeInfoFuture.get(TIMEOUT, TIMEOUT_UNIT);
    assertThat(result.getName(), is(name));
    assertThat(result.getFullname(), is(fullname));
    assertThat(result.getMessageType(), is(FafServerMessageType.GAME_TYPE_INFO));
    assertThat(result.getDesc(), is(description));
    assertThat(result.getIcon(), is(icon));
    assertThat(result.getOptions(), is(options));
  }

  private void connectAndLogIn() throws Exception {
    CompletableFuture<LoginMessage> loginFuture = instance.connectAndLogIn("JUnit", "JUnitPassword");

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

    MatchmakerMessage message = new MatchmakerMessage();
    message.setPotential(true);

    CompletableFuture<MatchmakerMessage> serviceStateDoneFuture = new CompletableFuture<>();

    WaitForAsyncUtils.waitForAsyncFx(200, () -> instance.addOnMessageListener(
        MatchmakerMessage.class, serviceStateDoneFuture::complete
    ));

    sendFromServer(message);

    MatchmakerMessage matchmakerServerMessage = serviceStateDoneFuture.get(TIMEOUT, TIMEOUT_UNIT);

    assertThat(matchmakerServerMessage.potential, is(true));
  }

  @Test
  public void startSearchRanked1v1WithAeon() throws Exception {
    connectAndLogIn();

    CompletableFuture<GameLaunchMessage> future = instance.startSearchRanked1v1(Faction.AEON, GAME_PORT);

    String clientMessage = messagesReceivedByFafServer.poll(TIMEOUT, TIMEOUT_UNIT);
    SearchRanked1V1ClientMessage searchRanked1v1Message = gson.fromJson(clientMessage, SearchRanked1V1ClientMessage.class);

    assertThat(searchRanked1v1Message, instanceOf(SearchRanked1V1ClientMessage.class));
    assertThat(searchRanked1v1Message.getFaction(), is(Faction.AEON));
    assertThat(searchRanked1v1Message.getGameport(), is(GAME_PORT));

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
