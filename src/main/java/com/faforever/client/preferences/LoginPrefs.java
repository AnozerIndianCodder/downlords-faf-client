package com.faforever.client.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class LoginPrefs {

  private final StringProperty username;
  private final StringProperty password;
  private final BooleanProperty autoLogin;
  private final BooleanProperty autoLoginForClan;

  public LoginPrefs() {
    username = new SimpleStringProperty();
    password = new SimpleStringProperty();
    autoLogin = new SimpleBooleanProperty();
    autoLoginForClan= new SimpleBooleanProperty(true);
  }

  public String getUsername() {
    return username.get();
  }

  public LoginPrefs setUsername(String username) {
    this.username.set(username);
    return this;
  }
  public Boolean getAutoLoginForClan() {
    return autoLoginForClan.get();
  }

  public void setAutoLoginForClan(Boolean autoLoginForClanInner) {
    this.autoLoginForClan.set(autoLoginForClanInner);

  }

  public StringProperty usernameProperty() {
    return username;
  }

  public String getPassword() {
    return password.get();
  }

  public LoginPrefs setPassword(String password) {
    this.password.set(password);
    return this;
  }

  public StringProperty passwordProperty() {
    return password;
  }

  public boolean getAutoLogin() {
    return autoLogin.get();
  }

  public LoginPrefs setAutoLogin(boolean autoLogin) {
    this.autoLogin.set(autoLogin);
    return this;
  }

  public BooleanProperty autoLoginProperty() {
    return autoLogin;
  }
}
