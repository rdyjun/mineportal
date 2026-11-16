package com.mineportal.ui;

import com.mineportal.BuildInfo;
import com.mineportal.update.AllowedLinks;
import com.mineportal.update.AppVersion;
import com.mineportal.update.DesktopSettings;
import com.mineportal.update.GithubReleaseClient;
import com.mineportal.update.InstallPlatform;
import com.mineportal.update.UpdateCheck;
import com.mineportal.update.UpdateChecker;
import com.mineportal.update.UpdateNotice;
import com.mineportal.update.UpdateOffer;
import com.mineportal.update.UpdateSchedule;
import com.mineportal.util.AppPaths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Update notice and the about window layered over the site. Checks never download or install; a
 * failed check is ignored unless the user asked for it.
 */
public final class DesktopChrome {

  private final StackPane stack = new StackPane();
  private final Button aboutButton = new Button();
  private final UpdateBanner banner;
  private final AboutWindow about;
  private final UpdateChecker checker;
  private final DesktopSettings settings;
  private final Consumer<String> openUrl;
  private final String repo;
  private UiText text;
  private AppVersion sessionDismissed;
  private ScheduledExecutorService scheduler;
  private boolean started;

  public DesktopChrome(
      BorderPane content,
      Stage owner,
      Consumer<String> openUrl,
      UpdateChecker checker,
      DesktopSettings settings,
      UiText text) {
    this.openUrl = openUrl == null ? url -> {} : openUrl;
    this.checker = checker;
    this.settings = settings;
    this.text = text == null ? UiText.system() : text;
    this.repo = BuildInfo.GITHUB_REPO;
    this.banner =
        new UpdateBanner(this.text, this::openOffer, this::dismissSession, this::ignoreVersion);
    this.about =
        new AboutWindow(
            owner, this.text, settings, this::openExternal, this::manualCheck, this::onAutoCheck);
    aboutButton.getStyleClass().add("about-button");
    aboutButton.setText(this.text.aboutButton());
    aboutButton.setOnAction(event -> about.show());
    aboutButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    banner.setMaxWidth(460);
    banner.setMaxHeight(Region.USE_PREF_SIZE);

    stack.getChildren().addAll(content, aboutButton, banner);
    stack.setStyle("-fx-background-color: #111111;");
    StackPane.setAlignment(aboutButton, Pos.BOTTOM_LEFT);
    StackPane.setMargin(aboutButton, new Insets(16));
    StackPane.setAlignment(banner, Pos.BOTTOM_RIGHT);
    StackPane.setMargin(banner, new Insets(16));
    stack.sceneProperty().addListener((obs, previous, scene) -> ChromeTheme.apply(scene));
  }

  public static DesktopChrome install(BorderPane content, Stage owner, Consumer<String> openUrl) {
    DesktopSettings settings =
        DesktopSettings.load(AppPaths.dir().resolve("desktop-settings.properties"));
    UpdateChecker checker =
        new UpdateChecker(
            AppVersion.parse(BuildInfo.VERSION),
            GithubReleaseClient.create(BuildInfo.GITHUB_REPO, BuildInfo.VERSION),
            InstallPlatform.detect(),
            BuildInfo.GITHUB_REPO);
    return new DesktopChrome(content, owner, openUrl, checker, settings, UiText.system());
  }

  public Parent root() {
    return stack;
  }

  public void start() {
    if (started) return;
    started = true;
    ScheduledExecutorService executor = executor();
    executor.schedule(this::auto, UpdateSchedule.INITIAL_DELAY.toSeconds(), TimeUnit.SECONDS);
    executor.scheduleWithFixedDelay(
        this::auto,
        UpdateSchedule.INTERVAL.toHours(),
        UpdateSchedule.INTERVAL.toHours(),
        TimeUnit.HOURS);
  }

  public void shutdown() {
    if (scheduler != null) scheduler.shutdownNow();
  }

  public void notePageLanguage(String language) {
    if (language == null || language.length() > 16) return;
    UiText next = UiText.forLanguage(language);
    if (next.korean() == text.korean()) return;
    text = next;
    aboutButton.setText(text.aboutButton());
    about.applyText(text);
    if (banner.offer() != null) banner.setOffer(banner.offer(), text);
  }

  void apply(UpdateCheck check, boolean manual) {
    if (check == null) check = UpdateCheck.failed();
    if (manual) about.setChecking(false);
    switch (check.status()) {
      case UPDATE_AVAILABLE -> showAvailable(check.offer(), manual);
      case UP_TO_DATE -> {
        banner.setShown(false);
        if (manual) about.setStatus(text.upToDate(BuildInfo.VERSION), "status-ok");
      }
      case CHECK_FAILED -> {
        if (manual) about.setStatus(text.checkFailed(), "status-muted");
      }
    }
  }

  private void showAvailable(UpdateOffer offer, boolean manual) {
    if (offer == null) {
      if (manual) about.setStatus(text.checkFailed(), "status-muted");
      return;
    }
    if (manual) about.setStatus(text.updateFound(offer.latest().toString()), "status-warn");
    if (!manual
        && !UpdateNotice.showAutomatically(
            UpdateCheck.available(offer), sessionDismissed, settings.ignoredVersion())) {
      return;
    }
    banner.setOffer(offer, text);
  }

  void openAboutForScreenshot() {
    about.show();
  }

  javafx.scene.Scene aboutSceneForScreenshot() {
    return about.stage().getScene();
  }

  private void manualCheck() {
    about.setChecking(true);
    about.setStatus(text.checking(), "status-muted");
    checkAsync(true);
  }

  private void onAutoCheck(boolean enabled) {
    settings.setAutoCheck(enabled);
    if (enabled) checkAsync(false);
  }

  private void auto() {
    try {
      if (!settings.autoCheck()) return;
      UpdateCheck result = checker.check();
      Platform.runLater(
          () -> {
            if (!settings.autoCheck()) return;
            apply(result, false);
          });
    } catch (Exception ex) {
      // A failed periodic check must not stop later checks or open a dialog.
    }
  }

  private void checkAsync(boolean manual) {
    executor()
        .execute(
            () -> {
              UpdateCheck result;
              try {
                result = checker.check();
              } catch (Exception ex) {
                result = UpdateCheck.failed();
              }
              UpdateCheck done = result;
              Platform.runLater(() -> apply(done, manual));
            });
  }

  private void openOffer(UpdateOffer offer) {
    openExternal(offer.openUrl());
  }

  private void openExternal(String url) {
    if (!AllowedLinks.isOpenable(url, repo)) return;
    try {
      openUrl.accept(url);
    } catch (RuntimeException ex) {
      // The browser can refuse a hand-off. Leave the notice in place.
    }
  }

  private void dismissSession(UpdateOffer offer) {
    sessionDismissed = offer.latest();
    banner.setShown(false);
  }

  private void ignoreVersion(UpdateOffer offer) {
    settings.setIgnoredVersion(offer.latest());
    sessionDismissed = offer.latest();
    banner.setShown(false);
  }

  private ScheduledExecutorService executor() {
    if (scheduler == null) {
      scheduler =
          Executors.newSingleThreadScheduledExecutor(
              runnable -> {
                Thread thread = new Thread(runnable, "mineportal-update-check");
                thread.setDaemon(true);
                return thread;
              });
    }
    return scheduler;
  }
}
