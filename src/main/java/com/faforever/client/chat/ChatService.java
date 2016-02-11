package com.faforever.client.chat;

import com.faforever.client.net.ConnectionState;
import javafx.beans.property.ObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import org.pircbotx.User;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ChatService {

  void addOnMessageListener(OnChatMessageListener listener);

  void addOnUserListListener(OnChatUserListListener listener);

  void addOnPrivateChatMessageListener(OnPrivateChatMessageListener listener);

  void addOnChatUserJoinedChannelListener(OnChatUserJoinedChannelListener listener);

  void addOnChatUserLeftChannelListener(OnChatUserLeftChannelListener listener);

  void addOnModeratorSetListener(OnModeratorSetListener listener);

  void addOnChatUserQuitListener(OnChatUserQuitListener listener);

  void connect();

  void disconnect();

  CompletableFuture<String> sendMessageInBackground(String target, String message);

  /**
   * Gets the list of chat users for the given channel as soon as it is available. <p> <strong>IMPORTANT:</strong> All
   * operations on the returned list must be synchronized, even iteration. Use the map as monitor. </p>
   */
  ObservableMap<String, ChatUser> getChatUsersForChannel(String channelName);

  ChatUser createOrGetChatUser(String username);

  void addChannelUserListListener(String channelName, MapChangeListener<String, ChatUser> listener);

  void leaveChannel(String channelName);

  CompletableFuture<String> sendActionInBackground(String target, String action);

  void joinChannel(String channelName);

  void addOnJoinChannelsRequestListener(Consumer<List<String>> listener);

  boolean isDefaultChannel(String channelName);

  void close();

  ChatUser createOrGetChatUser(User user);

  void addUserToColorListener();

  ObjectProperty<ConnectionState> connectionStateProperty();

  void reconnect();

  void whois(String username);
}
