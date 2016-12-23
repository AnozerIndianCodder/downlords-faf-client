package com.faforever.client.chat;

import com.faforever.client.audio.AudioService;
import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.player.Player;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.util.IdenticonUtil;
import com.google.common.annotations.VisibleForTesting;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebView;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.time.Instant;

import static com.faforever.client.chat.SocialStatus.FOE;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrivateChatTabController extends AbstractChatTabController {

  private final AudioService audioService;
  private final ChatService chatService;
  private final WebViewConfigurer webViewConfigurer;
  public Tab privateChatTabRoot;
  public WebView messagesWebView;
  public TextInputControl messageTextField;
  public ImageView userImageView;
  public Label usernameLabel;
  public Label ratingLabel;
  public Label gamesPlayedLabel;
  public Label inGameLabel;
  private boolean userOffline;

  @Inject
  public PrivateChatTabController(AudioService audioService, ChatService chatService, WebViewConfigurer webViewConfigurer) {
    this.audioService = audioService;
    this.chatService = chatService;
    this.webViewConfigurer = webViewConfigurer;
  }

  public boolean isUserOffline() {
    return userOffline;
  }

  @Override
  public Tab getRoot() {
    return privateChatTabRoot;
  }

  @Override
  public void setReceiver(String username) {
    super.setReceiver(username);
    privateChatTabRoot.setId(username);
    privateChatTabRoot.setText(username);

    //Load receiver information
    username = playerService.getCurrentPlayer().getUsername();//TODO: THIS IS FOR TESTING PURPOSES ONLY; REMOVE!
    Player player = playerService.getPlayerForUsername(username);
    usernameLabel.setText(username);
    userImageView.setImage(IdenticonUtil.createIdenticon(player.getId())) ;
    ratingLabel.setText("R: " + (int)(Math.round(player.getGlobalRatingMean())) + " +/- " + (int)(Math.round(player.getGlobalRatingDeviation())));
    gamesPlayedLabel.setText("G: " + player.getNumberOfGames());
    inGameLabel.setText(player.getGame() != null ? "In game" : "Not in game");//TODO: update
    //TODO: Game information
    //TODO: country
  }

  public void initialize() {
    userOffline = false;
    chatService.addChatUsersByNameListener(change -> {
      if (change.wasRemoved()) {
        onPlayerDisconnected(change.getKey(), change.getValueRemoved());
      }
      if (change.wasAdded()) {
        onPlayerConnected(change.getKey(), change.getValueRemoved());
      }
    });
    webViewConfigurer.configureWebView(messagesWebView);
  }

  @Override
  protected TextInputControl getMessageTextField() {
    return messageTextField;
  }

  @Override
  protected WebView getMessagesWebView() {
    return messagesWebView;
  }

  @Override
  public void onChatMessage(ChatMessage chatMessage) {
    Player player = playerService.getPlayerForUsername(chatMessage.getUsername());
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();

    if (player != null && player.getSocialStatus() == FOE && chatPrefs.getHideFoeMessages()) {
      return;
    }

    super.onChatMessage(chatMessage);

    if (!hasFocus()) {
      audioService.playPrivateMessageSound();
      showNotificationIfNecessary(chatMessage);
      setUnread(true);
      incrementUnreadMessagesCount(1);
    }
  }

  @VisibleForTesting
  void onPlayerDisconnected(String userName, ChatUser userItem) {
    if (userName.equals(getReceiver())) {
      userOffline = true;
      Platform.runLater(() -> onChatMessage(new ChatMessage(userName, Instant.now(), i18n.get("chat.operator") + ":", i18n.get("chat.privateMessage.playerLeft", userName), true)));
    }
  }

  @VisibleForTesting
  void onPlayerConnected(String userName, ChatUser userItem) {
    if (userOffline && userName.equals(getReceiver())) {
      userOffline = false;
      Platform.runLater(() -> onChatMessage(new ChatMessage(userName, Instant.now(), i18n.get("chat.operator") + ":", i18n.get("chat.privateMessage.playerReconnect", userName), true)));
    }
  }
}
