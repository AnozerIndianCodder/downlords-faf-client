package com.faforever.client.chat;

import com.faforever.client.fx.HostService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerInfoBean;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.uploader.ImageUploadService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.TimeService;
import javafx.collections.FXCollections;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChannelTabControllerTest extends AbstractPlainJavaFxTest {

  public static final String USER_NAME = "junit";
  private static final String CHANNEL_NAME = "#testChannel";

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();
  @Mock
  private ChatService chatService;
  @Mock
  private UserService userService;
  @Mock
  private ImageUploadService imageUploadService;
  @Mock
  private PlayerService playerService;
  @Mock
  private TimeService timeService;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private HostService hostService;
  @Mock
  private Preferences preferences;
  @Mock
  private ChatPrefs chatPrefs;
  @Mock
  private I18n i18n;
  @Mock
  private NotificationService notificationService;

  private ChannelTabController instance;

  @Before
  public void setUp() throws Exception {
    instance = loadController("channel_tab.fxml");
    instance.chatService = chatService;
    instance.userService = userService;
    instance.imageUploadService = imageUploadService;
    instance.playerService = playerService;
    instance.timeService = timeService;
    instance.notificationService = notificationService;
    instance.preferencesService = preferencesService;
    instance.hostService = hostService;
    instance.i18n = i18n;

    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferencesService.getCacheDirectory()).thenReturn(tempDir.getRoot().toPath());
    when(preferences.getTheme()).thenReturn("default");
    when(preferences.getChat()).thenReturn(chatPrefs);
    when(chatPrefs.getZoom()).thenReturn(1d);
    when(userService.getUsername()).thenReturn(USER_NAME);

    instance.postConstruct();

    TabPane tabPane = new TabPane();
    tabPane.getTabs().add(instance.getRoot());
    WaitForAsyncUtils.waitForAsyncFx(5000, () -> getRoot().getChildren().add(tabPane));
  }


  @Test
  public void testGetMessagesWebView() throws Exception {
    assertNotNull(instance.getMessagesWebView());
  }

  @Test
  public void testGetMessageTextField() throws Exception {
    assertNotNull(instance.getMessageTextField());
  }

  @Test
  public void testSetChannelName() throws Exception {
    when(chatService.getChatUsersForChannel(CHANNEL_NAME)).thenReturn(FXCollections.emptyObservableMap());

    instance.setChannelName(CHANNEL_NAME);

    verify(chatService).addChannelUserListListener(eq(CHANNEL_NAME), any());
  }

  @Test
  public void testGetMessageCssClassModerator() throws Exception {
    String playerName = "junit";
    when(chatService.getChatUsersForChannel(CHANNEL_NAME)).thenReturn(FXCollections.emptyObservableMap());

    PlayerInfoBean playerInfoBean = new PlayerInfoBean(playerName);
    playerInfoBean.moderatorForChannelsProperty().set(FXCollections.observableSet(CHANNEL_NAME));
    instance.setChannelName(CHANNEL_NAME);
    when(playerService.getPlayerForUsername(playerName)).thenReturn(playerInfoBean);
    assertEquals(instance.getMessageCssClass(playerName), ChannelTabController.CSS_CLASS_MODERATOR);
  }

  @Test
  public void onSearchFieldCloseTest() throws Exception {
    instance.onSearchFieldClose(null);
    assertTrue(!instance.searchField.isVisible());
    assertEquals("", instance.searchField.getText());
  }

  @Test
  public void onKeyReleasedTestEscape() throws Exception {
    KeyEvent keyEvent = new KeyEvent(null, null, null, null, null, KeyCode.ESCAPE, false, false, false, false);

    assertTrue(!instance.searchField.isVisible());
    instance.onKeyReleased(keyEvent);
    assertTrue(!instance.searchField.isVisible());
    assertEquals("", instance.searchField.getText());
  }

  @Test
  public void onKeyReleasedTestCtrlF() throws Exception {
    KeyEvent keyEvent = new KeyEvent(null, null, null, null, null, KeyCode.F, false, true, false, false);

    assertTrue(!instance.searchField.isVisible());
    instance.onKeyReleased(keyEvent);
    assertTrue(instance.searchField.isVisible());
    assertEquals("", instance.searchField.getText());
    instance.onKeyReleased(keyEvent);
    assertTrue(!instance.searchField.isVisible());
  }
}
