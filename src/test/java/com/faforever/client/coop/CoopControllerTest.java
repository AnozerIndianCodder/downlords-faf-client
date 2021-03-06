package com.faforever.client.coop;

import com.faforever.client.fx.WebViewConfigurer;
import com.faforever.client.game.FeaturedModBeanBuilder;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.game.GamesTableController;
import com.faforever.client.game.NewGameInfo;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.ModService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.layout.Pane;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CompletableFuture;

import static com.faforever.client.game.KnownFeaturedMod.COOP;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CoopControllerTest extends AbstractPlainJavaFxTest {
  @Rule
  public TemporaryFolder cacheDirectory = new TemporaryFolder();
  private CoopController instance;
  @Mock
  private CoopService coopService;
  @Mock
  private GameService gameService;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private UiService uiService;
  @Mock
  private GamesTableController gamesTableController;
  @Mock
  private MapService mapService;
  @Mock
  private I18n i18n;
  @Mock
  private WebViewConfigurer webViewConfigurer;
  @Mock
  private ModService modService;

  private SimpleObjectProperty<Game> selectedGameProperty;

  @Before
  public void setUp() throws Exception {
    instance = new CoopController();
    instance.coopService = coopService;
    instance.gameService = gameService;
    instance.preferencesService = preferencesService;
    instance.uiService = uiService;
    instance.mapService = mapService;
    instance.i18n = i18n;
    instance.webViewConfigurer = webViewConfigurer;
    instance.modService = modService;

    when(coopService.getLeaderboard(any(), anyInt())).thenReturn(CompletableFuture.completedFuture(emptyList()));
    when(modService.getFeaturedMod(COOP.getString())).thenReturn(CompletableFuture.completedFuture(FeaturedModBeanBuilder.create().defaultValues().technicalName("coop").get()));
    when(preferencesService.getCacheDirectory()).thenReturn(cacheDirectory.getRoot().toPath());
    when(gameService.getGames()).thenReturn(FXCollections.emptyObservableList());
    when(uiService.loadFxml("theme/play/games_table.fxml")).thenReturn(gamesTableController);
    when(gamesTableController.getRoot()).thenReturn(new Pane());
    selectedGameProperty = new SimpleObjectProperty<>();
    when(gamesTableController.selectedGameProperty()).thenReturn(selectedGameProperty);

    loadFxml("theme/play/coop/coop.fxml", clazz -> instance);

    verify(webViewConfigurer).configureWebView(instance.descriptionWebView);
  }

  @Test
  public void setUpIfNecessary() throws Exception {
    when(coopService.getMissions()).thenReturn(completedFuture(singletonList(new CoopMission())));

    instance.setUpIfNecessary();
    WaitForAsyncUtils.waitForFxEvents();

    verify(coopService).getMissions();
    assertThat(instance.missionComboBox.getItems(), hasSize(1));
  }

  @Test
  public void onPlayButtonClicked() throws Exception {
    when(coopService.getMissions()).thenReturn(completedFuture(singletonList(new CoopMission())));
    instance.setUpIfNecessary();

    WaitForAsyncUtils.waitForFxEvents();
    instance.onPlayButtonClicked();

    ArgumentCaptor<NewGameInfo> captor = ArgumentCaptor.forClass(NewGameInfo.class);
    verify(gameService).hostGame(captor.capture());

    NewGameInfo newGameInfo = captor.getValue();
    assertThat(newGameInfo.getFeaturedMod().getTechnicalName(), is("coop"));
  }

  @Test
  public void testGetRoot() throws Exception {
    assertThat(instance.getRoot(), is(instance.coopRoot));
    assertThat(instance.getRoot().getParent(), is(nullValue()));
  }
}
