package com.faforever.client.user;

import com.faforever.client.api.FafApiAccessor;
import com.faforever.client.legacy.domain.LoginMessage;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

public class UserServiceImpl implements UserService {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Resource
  FafService fafService;
  @Resource
  PreferencesService preferencesService;
  @Resource
  FafApiAccessor fafApiAccessor;

  private String username;
  private String password;
  private Integer uid;
  private BooleanProperty loggedIn;

  public UserServiceImpl() {
    loggedIn = new SimpleBooleanProperty();
  }

  @Override
  public BooleanProperty loggedInProperty() {
    return loggedIn;
  }

  @Override
  public CompletableFuture<Void> login(String username, String password, boolean autoLogin) {
    preferencesService.getPreferences().getLogin()
        .setUsername(username)
        .setPassword(password)
        .setAutoLogin(autoLogin);
    preferencesService.storeInBackground();

    this.username = username;
    this.password = password;

    return fafService.connectAndLogIn(username, password)
        .thenAccept(loginInfo -> {
          uid = loginInfo.getId();

          fafApiAccessor.authorize(loginInfo.getId());
          loggedIn.set(true);
        });
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public Integer getUid() {
    return uid;
  }

  @Override
  public void cancelLogin() {
    fafService.disconnect();
  }

  @Override
  public void logOut() {
    logger.info("Logging out");
    fafService.disconnect();
    loggedIn.set(false);
  }

  @PostConstruct
  void postConstruct() {
    fafService.addOnMessageListener(LoginMessage.class, loginInfo -> uid = loginInfo.getId());
  }
}
