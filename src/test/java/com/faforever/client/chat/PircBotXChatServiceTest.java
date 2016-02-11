package com.faforever.client.chat;

import com.faforever.client.i18n.I18n;
import com.faforever.client.legacy.domain.SocialMessage;
import com.faforever.client.net.ConnectionState;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.task.PrioritizedTask;
import com.faforever.client.task.TaskService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.user.UserService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Hashing;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import javafx.scene.paint.Color;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UserChannelDao;
import org.pircbotx.UserHostmask;
import org.pircbotx.UserLevel;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.QuitEvent;
import org.pircbotx.hooks.events.UserListEvent;
import org.pircbotx.hooks.managers.ListenerManager;
import org.pircbotx.output.OutputChannel;
import org.pircbotx.output.OutputIRC;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.faforever.client.chat.ChatColorMode.CUSTOM;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PircBotXChatServiceTest extends AbstractPlainJavaFxTest {

  public static final String CHAT_USER_NAME = "junit";
  public static final String CHAT_PASSWORD = "123";
  private static final InetAddress LOOPBACK_ADDRESS = InetAddress.getLoopbackAddress();
  private static final long TIMEOUT = 3000;
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
  private static final String DEFAULT_CHANNEL_NAME = "#defaultChannel";
  private static final String OTHER_CHANNEL_NAME = "#otherChannel";
  private static final int IRC_SERVER_PORT = 123;
  private PircBotXChatService instance;

  private ChatUser chatUser1;
  private ChatUser chatUser2;

  @Mock
  private User user1;
  @Mock
  private User user2;
  @Mock
  private Channel defaultChannel;
  @Mock
  private PircBotX pircBotX;
  @Mock
  private Configuration configuration;
  @Mock
  private ListenerManager listenerManager;
  @Mock
  private UserChannelDaoSnapshot daoSnapshot;
  @Mock
  private UserSnapshot userSnapshot;
  @Mock
  private ChannelSnapshot channelSnapshot;
  @Mock
  private OutputIRC outputIrc;
  @Mock
  private UserService userService;
  @Mock
  private TaskService taskService;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private Preferences preferences;
  @Mock
  private ChatPrefs chatPrefs;
  @Mock
  private I18n i18n;
  @Mock
  private PircBotXFactory pircBotXFactory;
  @Mock
  private UserChannelDao<User, Channel> userChannelDao;
  @Mock
  private MapProperty<String, Color> userToColorProperty;
  @Mock
  private ObjectProperty<ChatColorMode> chatColorMode;
  @Mock
  private FafService fafService;
  @Mock
  private ExecutorService executorService;
  @Mock
  private UserHostmask userHostMask;

  private CountDownLatch botShutdownLatch;
  private CompletableFuture<Object> botStartedFuture;
  private BooleanProperty loggedInProperty;

  @Before
  public void setUp() throws Exception {
    instance = new PircBotXChatService();
    instance.fafService = fafService;
    instance.userService = userService;
    instance.taskService = taskService;
    instance.i18n = i18n;
    instance.pircBotXFactory = pircBotXFactory;
    instance.preferencesService = preferencesService;
    instance.executorService = executorService;

    chatUser1 = new ChatUser("chatUser1", null);
    chatUser2 = new ChatUser("chatUser2", null);
    loggedInProperty = new SimpleBooleanProperty();

    botShutdownLatch = new CountDownLatch(1);

    userToColorProperty = new SimpleMapProperty<>(FXCollections.observableHashMap());
    chatColorMode = new SimpleObjectProperty<>(CUSTOM);

    when(userService.getUsername()).thenReturn(CHAT_USER_NAME);
    when(userService.getPassword()).thenReturn(CHAT_PASSWORD);
    when(userService.loggedInProperty()).thenReturn(loggedInProperty);

    when(user1.getNick()).thenReturn(chatUser1.getUsername());
    when(user1.getChannels()).thenReturn(ImmutableSortedSet.of(defaultChannel));
    when(user1.getUserLevels(defaultChannel)).thenReturn(ImmutableSortedSet.of(UserLevel.VOICE));

    when(user2.getNick()).thenReturn(chatUser2.getUsername());
    when(user2.getChannels()).thenReturn(ImmutableSortedSet.of(defaultChannel));
    when(user2.getUserLevels(defaultChannel)).thenReturn(ImmutableSortedSet.of(UserLevel.VOICE));

    when(defaultChannel.getName()).thenReturn(DEFAULT_CHANNEL_NAME);
    when(pircBotX.getConfiguration()).thenReturn(configuration);
    when(pircBotX.sendIRC()).thenReturn(outputIrc);
    when(pircBotX.getUserChannelDao()).thenReturn(userChannelDao);

    doAnswer(invocation -> {
      CompletableFuture<Object> future = new CompletableFuture<>();
      WaitForAsyncUtils.async(() -> {
        invocation.getArgumentAt(0, Task.class).run();
        future.complete(null);
      });
      return future;
    }).when(executorService).submit(any(Task.class));

    botStartedFuture = new CompletableFuture<>();
    doAnswer(invocation -> {
      botStartedFuture.complete(true);
      botShutdownLatch.await();
      return null;
    }).when(pircBotX).startBot();

    when(pircBotXFactory.createPircBotX(any())).thenReturn(pircBotX);
    when(configuration.getListenerManager()).thenReturn(listenerManager);

    instance.ircHost = LOOPBACK_ADDRESS.getHostAddress();
    instance.ircPort = IRC_SERVER_PORT;
    instance.defaultChannelName = DEFAULT_CHANNEL_NAME;
    instance.reconnectDelay = 100;

    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferences.getChat()).thenReturn(chatPrefs);

    when(chatPrefs.userToColorProperty()).thenReturn(userToColorProperty);
    when(chatPrefs.chatColorModeProperty()).thenReturn(chatColorMode);

    instance.postConstruct();
  }

  @After
  public void tearDown() {
    instance.close();
    botShutdownLatch.countDown();
  }

  @Test
  public void testOnChatUserList() throws Exception {
    ObservableMap<String, ChatUser> usersForChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);
    assertThat(usersForChannel.values(), empty());

    Map<String, ChatUser> users = new HashMap<>();
    users.put(chatUser1.getUsername(), chatUser1);
    users.put(chatUser2.getUsername(), chatUser2);

    instance.onChatUserList(DEFAULT_CHANNEL_NAME, users);

    assertThat(usersForChannel.values(), hasSize(2));
    assertThat(usersForChannel.get(chatUser1.getUsername()), sameInstance(chatUser1));
    assertThat(usersForChannel.get(chatUser2.getUsername()), sameInstance(chatUser2));
  }

  @Test
  public void testOnUserJoinedChannel() throws Exception {
    ObservableMap<String, ChatUser> usersForChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);
    assertThat(usersForChannel.values(), empty());

    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser2);

    assertThat(usersForChannel.values(), hasSize(2));
    assertThat(usersForChannel.get(chatUser1.getUsername()), sameInstance(chatUser1));
    assertThat(usersForChannel.get(chatUser2.getUsername()), sameInstance(chatUser2));
  }

  @Test
  public void testOnChatUserLeftChannel() throws Exception {
    ObservableMap<String, ChatUser> usersForChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);
    assertThat(usersForChannel.values(), empty());

    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser2);
    instance.onChatUserLeftChannel(chatUser1.getUsername(), DEFAULT_CHANNEL_NAME);

    assertThat(usersForChannel.values(), hasSize(1));
    assertThat(usersForChannel.get(chatUser2.getUsername()), sameInstance(chatUser2));
  }

  @Test
  public void testOnChatUserQuit() throws Exception {
    ObservableMap<String, ChatUser> usersForChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);
    assertThat(usersForChannel.values(), empty());

    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser2);
    instance.onChatUserQuit(chatUser1.getUsername());

    assertThat(usersForChannel.values(), hasSize(1));
    assertThat(usersForChannel.get(chatUser2.getUsername()), sameInstance(chatUser2));
  }

  @Test
  public void testAddOnMessageListenerWithMessage() throws Exception {
    CompletableFuture<String> channelNameFuture = new CompletableFuture<>();
    CompletableFuture<ChatMessage> chatMessageFuture = new CompletableFuture<>();
    instance.addOnMessageListener((channelName, chatMessage) -> {
      channelNameFuture.complete(channelName);
      chatMessageFuture.complete(chatMessage);
    });

    String message = "chat message";

    User user = mock(User.class);
    when(user.getNick()).thenReturn(chatUser1.getUsername());

    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn(DEFAULT_CHANNEL_NAME);

    UserHostmask userHostMask = mock(UserHostmask.class);
    ImmutableMap<String, String> tags = new ImmutableMap.Builder<String, String>().build();
    instance.onEvent(new MessageEvent(pircBotX, channel, DEFAULT_CHANNEL_NAME, userHostMask, user, message, tags));

    assertThat(channelNameFuture.get(), is(DEFAULT_CHANNEL_NAME));
    assertThat(chatMessageFuture.get().getMessage(), is(message));
    assertThat(chatMessageFuture.get().getUsername(), is(chatUser1.getUsername()));
    assertThat(chatMessageFuture.get().getTime(), is(greaterThan(Instant.ofEpochMilli(System.currentTimeMillis() - 1000))));
    assertThat(chatMessageFuture.get().isAction(), is(false));
  }

  @Test
  public void testAddOnMessageListenerWithAction() throws Exception {
    CompletableFuture<String> channelNameFuture = new CompletableFuture<>();
    CompletableFuture<ChatMessage> chatMessageFuture = new CompletableFuture<>();
    instance.addOnMessageListener((channelName, chatMessage) -> {
      channelNameFuture.complete(channelName);
      chatMessageFuture.complete(chatMessage);
    });

    String action = "chat action";

    User user = mock(User.class);
    when(user.getNick()).thenReturn(chatUser1.getUsername());

    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn(DEFAULT_CHANNEL_NAME);

    UserHostmask userHostMask = mock(UserHostmask.class);
    instance.onEvent(new ActionEvent(pircBotX, userHostMask, user, channel, DEFAULT_CHANNEL_NAME, action));

    assertThat(channelNameFuture.get(), is(DEFAULT_CHANNEL_NAME));
    assertThat(chatMessageFuture.get().getMessage(), is(action));
    assertThat(chatMessageFuture.get().getUsername(), is(chatUser1.getUsername()));
    assertThat(chatMessageFuture.get().getTime(), is(greaterThan(Instant.ofEpochMilli(System.currentTimeMillis() - 1000))));
    assertThat(chatMessageFuture.get().isAction(), is(true));
  }

  @Test
  public void testAddOnPrivateChatMessageListener() throws Exception {
    CompletableFuture<String> usernameFuture = new CompletableFuture<>();
    CompletableFuture<ChatMessage> chatMessageFuture = new CompletableFuture<>();
    instance.addOnPrivateChatMessageListener((username, chatMessage) -> {
      usernameFuture.complete(username);
      chatMessageFuture.complete(chatMessage);
    });

    String message = "private message";

    User user = mock(User.class);
    when(user.getNick()).thenReturn(chatUser1.getUsername());

    Channel channel = mock(Channel.class);
    when(channel.getName()).thenReturn(DEFAULT_CHANNEL_NAME);

    UserHostmask userHostMask = mock(UserHostmask.class);
    instance.onEvent(new PrivateMessageEvent(pircBotX, userHostMask, user, message));

    assertThat(chatMessageFuture.get().getMessage(), is(message));
    assertThat(chatMessageFuture.get().getUsername(), is(chatUser1.getUsername()));
    assertThat(chatMessageFuture.get().getTime(), is(greaterThan(Instant.ofEpochMilli(System.currentTimeMillis() - 1000))));
    assertThat(chatMessageFuture.get().isAction(), is(false));
  }

  @Test
  public void testAddOnChatConnectedListener() throws Exception {
    CompletableFuture<Boolean> onChatConnectedFuture = new CompletableFuture<>();

    instance.connectionStateProperty().addListener((observable, oldValue, newValue) -> {
      switch (newValue) {
        case CONNECTED:
          onChatConnectedFuture.complete(null);
          break;
      }
    });

    String password = "123";
    when(userService.getPassword()).thenReturn(password);

    mockTaskService();

    instance.onEvent(new ConnectEvent(pircBotX));

    assertThat(onChatConnectedFuture.get(TIMEOUT, TIMEOUT_UNIT), is(nullValue()));
  }

  @SuppressWarnings("unchecked")
  private void mockTaskService() {
    doAnswer((InvocationOnMock invocation) -> {
      PrioritizedTask<Boolean> prioritizedTask = invocation.getArgumentAt(0, PrioritizedTask.class);
      prioritizedTask.run();

      Future<Boolean> result = WaitForAsyncUtils.asyncFx(prioritizedTask::getValue);
      return CompletableFuture.completedFuture(result.get(1, TimeUnit.SECONDS));
    }).when(instance.taskService).submitTask(any());
  }

  @Test
  public void testAddOnUserListListener() throws Exception {
    CompletableFuture<String> channelNameFuture = new CompletableFuture<>();
    CompletableFuture<Map<String, ChatUser>> usersFuture = new CompletableFuture<>();
    instance.addOnUserListListener((channelName, users) -> {
      channelNameFuture.complete(channelName);
      usersFuture.complete(users);
    });
    when(chatPrefs.getChatColorMode()).thenReturn(chatColorMode.get());
    when(chatPrefs.getUserToColor()).thenReturn(userToColorProperty);

    when(user2.compareTo(user1)).thenReturn(1);
    ImmutableSortedSet<User> users = ImmutableSortedSet.of(user1, user2);
    instance.onEvent(new UserListEvent(pircBotX, defaultChannel, users, true));

    assertThat(channelNameFuture.get(TIMEOUT, TIMEOUT_UNIT), is(DEFAULT_CHANNEL_NAME));

    Map<String, ChatUser> userMap = usersFuture.get(TIMEOUT, TIMEOUT_UNIT);
    assertThat(userMap.values(), hasSize(2));
    assertThat(userMap.get(chatUser1.getUsername()), is(chatUser1));
    assertThat(userMap.get(chatUser2.getUsername()), is(chatUser2));
  }

  @Test
  public void testAddOnChatDisconnectedListener() throws Exception {
    CompletableFuture<Void> onChatDisconnectedFuture = new CompletableFuture<>();
    instance.connectionStateProperty().addListener((observable, oldValue, newValue) -> {
      switch (newValue) {
        case DISCONNECTED:
          onChatDisconnectedFuture.complete(null);
          break;
      }
    });

    instance.onEvent(new DisconnectEvent(pircBotX, daoSnapshot, null));

    onChatDisconnectedFuture.get(TIMEOUT, TIMEOUT_UNIT);
  }

  @Test
  public void testAddOnChatUserJoinedChannelListener() throws Exception {
    when(chatPrefs.getChatColorMode()).thenReturn(chatColorMode.get());
    when(chatPrefs.getUserToColor()).thenReturn(userToColorProperty);

    CompletableFuture<String> channelNameFuture = new CompletableFuture<>();
    CompletableFuture<ChatUser> userFuture = new CompletableFuture<>();
    instance.addOnChatUserJoinedChannelListener((channelName, chatUser) -> {
      channelNameFuture.complete(channelName);
      userFuture.complete(chatUser);
    });

    instance.onEvent(new JoinEvent(pircBotX, defaultChannel, userHostMask, user1));

    assertThat(channelNameFuture.get(TIMEOUT, TIMEOUT_UNIT), is(DEFAULT_CHANNEL_NAME));
    assertThat(userFuture.get(TIMEOUT, TIMEOUT_UNIT), is(chatUser1));
  }

  @Test
  public void testAddOnChatUserLeftChannelListener() throws Exception {
    CompletableFuture<String> usernameFuture = new CompletableFuture<>();
    CompletableFuture<String> channelNameFuture = new CompletableFuture<>();
    instance.addOnChatUserLeftChannelListener((username, channelName) -> {
      usernameFuture.complete(username);
      channelNameFuture.complete(channelName);
    });

    when(channelSnapshot.getName()).thenReturn(DEFAULT_CHANNEL_NAME);
    when(userSnapshot.getNick()).thenReturn(chatUser1.getUsername());

    String reason = "Part reason";
    instance.onEvent(new PartEvent(pircBotX, daoSnapshot, channelSnapshot, userHostMask, userSnapshot, reason));

    assertThat(channelNameFuture.get(TIMEOUT, TIMEOUT_UNIT), is(DEFAULT_CHANNEL_NAME));
    assertThat(usernameFuture.get(TIMEOUT, TIMEOUT_UNIT), is(chatUser1.getUsername()));
  }

  @Test
  public void testAddOnModeratorSetListener() throws Exception {

    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);

    ObservableSet<String> moderatorInChannels = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME).get(chatUser1.getUsername()).getModeratorInChannels();

    assertThat(moderatorInChannels, empty());

    instance.onModeratorSet(DEFAULT_CHANNEL_NAME, chatUser1.getUsername());

    assertThat(moderatorInChannels, hasSize(1));
    assertThat(moderatorInChannels.iterator().next(), is(DEFAULT_CHANNEL_NAME));
  }

  @Test
  public void testAddOnChatUserQuitListener() throws Exception {
    CompletableFuture<Boolean> quitFuture = new CompletableFuture<>();
    instance.addOnChatUserQuitListener((username) -> quitFuture.complete(true));

    instance.onEvent(new QuitEvent(pircBotX, daoSnapshot, userHostMask, userSnapshot, "reason"));

    assertThat(quitFuture.get(TIMEOUT, TIMEOUT_UNIT), is(true));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testConnect() throws Exception {
    ArgumentCaptor<Configuration> captor = ArgumentCaptor.forClass(Configuration.class);
    when(userService.getUid()).thenReturn(681);

    instance.connect();
    botStartedFuture.get(TIMEOUT, TIMEOUT_UNIT);

    verify(pircBotX).startBot();
    verify(pircBotXFactory).createPircBotX(captor.capture());
    Configuration configuration = captor.getValue();

    assertThat(configuration.getName(), is(CHAT_USER_NAME));
    assertThat(configuration.getLogin(), is("681"));
    assertThat(configuration.getRealName(), is(CHAT_USER_NAME));
    assertThat(configuration.getServers().get(0).getHostname(), is(LOOPBACK_ADDRESS.getHostAddress()));
    assertThat(configuration.getServers().get(0).getPort(), is(IRC_SERVER_PORT));
  }

  @Test
  public void testReconnect() throws Exception {
    CompletableFuture<Boolean> firstStartFuture = new CompletableFuture<>();
    CompletableFuture<Boolean> secondStartFuture = new CompletableFuture<>();
    doAnswer(invocation -> {
      if (!firstStartFuture.isDone()) {
        firstStartFuture.complete(true);
        throw new IOException("test exception");
      }

      secondStartFuture.complete(true);
      botShutdownLatch.await();
      return null;
    }).when(pircBotX).startBot();

    instance.connect();
    firstStartFuture.get(TIMEOUT, TIMEOUT_UNIT);

    secondStartFuture.get(TIMEOUT, TIMEOUT_UNIT);

    verify(pircBotX, times(2)).startBot();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSendMessageInBackground() throws Exception {
    instance.connect();

    String message = "test message";

    mockTaskService();

    CompletableFuture<String> future = instance.sendMessageInBackground(DEFAULT_CHANNEL_NAME, message);

    assertThat(future.get(TIMEOUT, TIMEOUT_UNIT), is(message));
    verify(pircBotX).sendIRC();
    verify(outputIrc).message(DEFAULT_CHANNEL_NAME, message);
  }

  @Test
  public void testGetChatUsersForChannelEmpty() throws Exception {
    ObservableMap<String, ChatUser> chatUsersForChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);
    assertThat(chatUsersForChannel.values(), empty());
  }

  @Test
  public void testGetChatUsersForChannelTwoUsersInDifferentChannel() throws Exception {
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    instance.onUserJoinedChannel(OTHER_CHANNEL_NAME, chatUser2);

    ObservableMap<String, ChatUser> chatUsersForDefaultChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);
    ObservableMap<String, ChatUser> chatUsersForOtherChannel = instance.getChatUsersForChannel(OTHER_CHANNEL_NAME);

    assertThat(chatUsersForDefaultChannel.values(), hasSize(1));
    assertThat(chatUsersForDefaultChannel.values().iterator().next(), sameInstance(chatUser1));
    assertThat(chatUsersForOtherChannel.values(), hasSize(1));
    assertThat(chatUsersForOtherChannel.values().iterator().next(), sameInstance(chatUser2));
  }

  @Test
  public void testGetChatUsersForChannelTwoUsersInSameChannel() throws Exception {
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser2);

    ObservableMap<String, ChatUser> chatUsersForDefaultChannel = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME);

    assertThat(chatUsersForDefaultChannel.values(), hasSize(2));
    assertThat(chatUsersForDefaultChannel.values(), containsInAnyOrder(chatUser1, chatUser2));
  }

  @Test
  public void testAddChannelUserListListener() throws Exception {
    @SuppressWarnings("unchecked")
    MapChangeListener<String, ChatUser> listener = mock(MapChangeListener.class);

    instance.addChannelUserListListener(DEFAULT_CHANNEL_NAME, listener);
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser2);

    verify(listener, times(2)).onChanged(any());
  }

  @Test
  public void testLeaveChannel() throws Exception {
    OutputChannel outputChannel = mock(OutputChannel.class);

    when(userChannelDao.getChannel(DEFAULT_CHANNEL_NAME)).thenReturn(defaultChannel);
    when(defaultChannel.send()).thenReturn(outputChannel);

    instance.connect();
    instance.leaveChannel(DEFAULT_CHANNEL_NAME);

    verify(outputChannel).part();
  }

  @Test
  public void testSendActionInBackground() throws Exception {
    instance.connect();

    String action = "test action";

    when(taskService.submitTask(any())).thenReturn(CompletableFuture.completedFuture(action));
    mockTaskService();

    CompletableFuture<String> future = instance.sendActionInBackground(DEFAULT_CHANNEL_NAME, action);

    verify(pircBotX).sendIRC();
    verify(outputIrc).action(DEFAULT_CHANNEL_NAME, action);
    assertThat(future.get(TIMEOUT, TIMEOUT_UNIT), is(action));
  }

  @Ignore("hangs when run with other tests")
  @Test
  public void testJoinChannel() throws Exception {
    CountDownLatch connectedLatch = new CountDownLatch(1);
    String channelToJoin = "#anotherChannel";
    doAnswer(invocation -> {
      connectedLatch.countDown();
      return null;
    }).when(outputIrc).joinChannel(DEFAULT_CHANNEL_NAME);

    when(taskService.submitTask(any())).thenReturn(CompletableFuture.completedFuture(null));

    instance.connect();
    instance.connectionStateProperty().set(ConnectionState.CONNECTED);

    assertTrue(connectedLatch.await(TIMEOUT, TIMEOUT_UNIT));

    instance.joinChannel(channelToJoin);

    verify(outputIrc).joinChannel(DEFAULT_CHANNEL_NAME);
    verify(outputIrc).joinChannel(channelToJoin);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testAddOnJoinChannelsRequestListener() throws Exception {
    Consumer<List<String>> listener = mock(Consumer.class);

    instance.addOnJoinChannelsRequestListener(listener);

    verify(fafService).addOnMessageListener(eq(SocialMessage.class), any());
  }

  @Test
  public void testIsDefaultChannel() throws Exception {
    assertTrue(instance.isDefaultChannel(DEFAULT_CHANNEL_NAME));
  }

  @Test
  public void testOnConnected() throws Exception {
    String password = "123";

    when(userService.getPassword()).thenReturn(password);
    instance.connect();

    botStartedFuture.get(TIMEOUT, TIMEOUT_UNIT);

    CountDownLatch latch = new CountDownLatch(1);
    doAnswer(invocation -> {
      latch.countDown();
      return null;
    }).when(outputIrc).joinChannel(DEFAULT_CHANNEL_NAME);

    mockTaskService();
    instance.connectionStateProperty().set(ConnectionState.CONNECTED);

    String md5Password = Hashing.md5().hashString(password, StandardCharsets.UTF_8).toString();
    verify(outputIrc).message("NICKSERV", String.format("IDENTIFY %s", md5Password));

    assertTrue("Channel has not been joined within timeout", latch.await(TIMEOUT, TIMEOUT_UNIT));
  }

  private String asdf() {
    return null;
  }

  @Test
  public void testOnDisconnected() throws Exception {
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    assertThat(instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME).values(), hasSize(1));

    instance.connectionStateProperty().set(ConnectionState.DISCONNECTED);

    assertThat(instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME).values(), empty());
  }

  @Test
  public void testOnModeratorSet() throws Exception {
    instance.onUserJoinedChannel(DEFAULT_CHANNEL_NAME, chatUser1);
    ObservableSet<String> moderatorInChannels = instance.getChatUsersForChannel(DEFAULT_CHANNEL_NAME)
        .get(chatUser1.getUsername())
        .getModeratorInChannels();

    assertThat(moderatorInChannels, empty());

    instance.onModeratorSet(DEFAULT_CHANNEL_NAME, chatUser1.getUsername());

    assertThat(moderatorInChannels, contains(DEFAULT_CHANNEL_NAME));
  }

  @Test
  public void testOnModeratorSetUserNotInChannelDoesntThrowException() throws Exception {
    instance.onModeratorSet(DEFAULT_CHANNEL_NAME, chatUser1.getUsername());
  }

  @Test
  public void testClose() {
    instance.close();
  }

  @Test
  public void testOnConnectedAutomaticallyRegisters() {

  }

  @Test
  public void testCreateOrGetChatUserStringEmptyMap() throws Exception {
    when(chatPrefs.getChatColorMode()).thenReturn(chatColorMode.get());
    when(chatPrefs.getUserToColor()).thenReturn(userToColorProperty);

    ChatUser returnedUser = instance.createOrGetChatUser("chatUser1");

    assert returnedUser != chatUser1;
    assertEquals(returnedUser, chatUser1);
  }

  @Test
  public void testCreateOrGetChatUserStringPopulatedMap() throws Exception {
    when(chatPrefs.getChatColorMode()).thenReturn(chatColorMode.get());
    when(chatPrefs.getUserToColor()).thenReturn(userToColorProperty);

    ChatUser addedUser = instance.createOrGetChatUser("chatUser1");
    ChatUser returnedUser = instance.createOrGetChatUser("chatUser1");

    assert returnedUser == addedUser;
    assertEquals(returnedUser, addedUser);
  }

  @Test
  public void testCreateOrGetChatUserUserObjectEmptyMap() throws Exception {
    when(chatPrefs.getChatColorMode()).thenReturn(chatColorMode.get());
    when(chatPrefs.getUserToColor()).thenReturn(userToColorProperty);

    ChatUser returnedUser = instance.createOrGetChatUser("chatUser1");

    assert returnedUser != chatUser1;
    assertEquals(returnedUser, chatUser1);
  }

  @Test
  public void testCreateOrGetChatUserUserObjectPopulatedMap() throws Exception {
    when(chatPrefs.getChatColorMode()).thenReturn(chatColorMode.get());
    when(chatPrefs.getUserToColor()).thenReturn(userToColorProperty);

    ChatUser addedUser = instance.createOrGetChatUser("chatUser1");
    ChatUser returnedUser = instance.createOrGetChatUser("chatUser1");

    assert returnedUser == addedUser;
    assertEquals(returnedUser, addedUser);
  }
}
