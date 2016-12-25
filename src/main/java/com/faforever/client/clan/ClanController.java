package com.faforever.client.clan;

import com.faforever.client.fx.AbstractViewController;
import com.faforever.client.preferences.LoginPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.google.common.base.Strings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.NodeList;
import org.w3c.dom.html.HTMLButtonElement;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.lang.invoke.MethodHandles;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ClanController extends AbstractViewController<Node> {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public WebView clanRoot;
  @Inject
  PreferencesService preferencesService;
  LoginPrefs login;
  @Value("${clanWebsite.url}")
  private String clanWebsiteUrl;
  @PostConstruct
  public void init() {
    login = preferencesService.getPreferences().getLogin();
  }

  public void onDisplay() {
    if (Strings.isNullOrEmpty(clanRoot.getEngine().getLocation())) {
      clanRoot.getEngine().load(clanWebsiteUrl);
      clanRoot.getEngine().setJavaScriptEnabled(true);
      clanRoot.getEngine().documentProperty().addListener(new ChangeListener<org.w3c.dom.Document>() {
        @Override
        public void changed(ObservableValue<? extends org.w3c.dom.Document> observable, org.w3c.dom.Document oldValue, org.w3c.dom.Document newValue) {
          onSiteLoaded();
        }
      });
    }
  }

  public void onSiteLoaded() {
    try {
      org.w3c.dom.Document site = clanRoot.getEngine().getDocument();
      org.w3c.dom.Element username = site.getElementById("login_form_username_input");
      if (username == null) {
        throw new Exception("usernameField not found. Is this the main Page?");
      }
      username.setAttribute("value", login.getUsername());
      NodeList elemtenList = site.getElementsByTagName("input");
      org.w3c.dom.Element passwordElement = (org.w3c.dom.Element) elemtenList.item(1);
      passwordElement.setAttribute("value", "");
      HTMLButtonElement button = (HTMLButtonElement) site.getElementsByTagName("button").item(0);
      //button.getForm().submit();
      //clanRoot.getEngine().reload();
    } catch (Exception e) {
      logger.warn(e.toString() + " consider this might be triggered also if another page then the front page is loaded");
    }
  }

  public Node getRoot() {
    return clanRoot;
  }
}


