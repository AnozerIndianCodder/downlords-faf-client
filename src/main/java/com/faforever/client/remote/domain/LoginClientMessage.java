package com.faforever.client.remote.domain;

import java.util.Collection;
import java.util.Collections;

public class LoginClientMessage extends ClientMessage {

  private String login;
  private String password;
  private long session;
  private String uniqueId;

  public LoginClientMessage(String username, String password, long session, String uniqueId) {
    super(ClientMessageType.LOGIN);
    this.setLogin(username);
    this.setPassword(password);
    this.setSession(session);
    this.setUniqueId(uniqueId);
  }

  @Override
  public Collection<String> getStringsToMask() {
    return Collections.singletonList(getPassword());
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public long getSession() {
    return session;
  }

  public void setSession(long session) {
    this.session = session;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }
}
