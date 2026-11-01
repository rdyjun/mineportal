package com.mineportal.ui;

import com.mineportal.net.BackendClient;
import com.mineportal.pair.ProtocolRegistrar;
import java.net.URI;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** Shares the production web UI while Minecraft connections run in this process. */
public final class DesktopApp extends Application {
  private static BackendClient backend;
  private final URI site = URI.create("https://mineportal.kr/");

  public static void open(BackendClient client) {
    backend = client;
    launch(DesktopApp.class);
  }

  @Override
  public void start(Stage stage) {
    WebView view = new WebView();
    WebEngine engine = view.getEngine();
    // This changes presentation only. Account authentication and short-lived pairing are
    // still enforced by the backend, exactly as for the browser.
    engine.setUserAgent(engine.getUserAgent() + " MinePortalDesktop/1.1");
    engine.setOnStatusChanged(
        event -> {
          if (!trusted(engine.getLocation())) return;
          String code = ProtocolRegistrar.extractPairCode(new String[] {event.getData()});
          if (code != null && code.matches("[A-Z0-9]{4,16}")) backend.pairImmediately(code);
        });
    engine.setOnAlert(
        event -> new Alert(Alert.AlertType.INFORMATION, event.getData()).showAndWait());
    engine.setConfirmHandler(
        message -> {
          Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message);
          return alert
              .showAndWait()
              .filter(button -> button == javafx.scene.control.ButtonType.OK)
              .isPresent();
        });
    // Public server directories and documentation open in the user's browser.
    engine.setCreatePopupHandler(
        features -> {
          WebEngine popup = new WebEngine();
          popup
              .locationProperty()
              .addListener(
                  (observable, oldUrl, url) -> {
                    if (url.startsWith("https://")) {
                      getHostServices().showDocument(url);
                      popup.getLoadWorker().cancel();
                    }
                  });
          return popup;
        });
    BorderPane root = new BorderPane(view);
    root.setStyle("-fx-background-color: #111111;");
    engine
        .locationProperty()
        .addListener(
            (observable, oldUrl, url) -> {
              if (!url.startsWith("mineportal:")) return;
              engine.getLoadWorker().cancel();
              // No Java object is exposed to remote JavaScript. Only the official site's
              // existing pairing URL is accepted, and no tokens travel in that URL.
              if (trusted(oldUrl)) {
                String code = ProtocolRegistrar.extractPairCode(new String[] {url});
                if (code != null && code.matches("[A-Z0-9]{4,16}")) backend.pairImmediately(code);
              }
            });
    engine
        .getLoadWorker()
        .stateProperty()
        .addListener(
            (observable, oldState, state) -> {
              if (state == Worker.State.SUCCEEDED) root.setCenter(view);
              if (state == Worker.State.FAILED && !engine.getLocation().startsWith("mineportal:")) {
                Button retry = new Button("다시 연결 / Retry");
                retry.setOnAction(
                    event -> {
                      root.setCenter(view);
                      engine.load(site.toString());
                    });
                VBox error = new VBox(16, new Label("마인포탈에 연결할 수 없어요. 인터넷 연결을 확인해주세요."), retry);
                error.setStyle("-fx-alignment: center; -fx-background-color: #eeeeee;");
                root.setCenter(error);
              }
            });
    stage.setTitle("마인포탈 · MinePortal");
    stage.setMinWidth(900);
    stage.setMinHeight(600);
    stage.setScene(new Scene(root, 1280, 820));
    stage.show();
    engine.load(site.toString());
  }

  private boolean trusted(String url) {
    try {
      URI uri = URI.create(url);
      return site.getScheme().equals(uri.getScheme())
          && site.getAuthority().equals(uri.getAuthority());
    } catch (IllegalArgumentException error) {
      return false;
    }
  }

  @Override
  public void stop() {
    // All active Minecraft sockets must be closed when the application window closes.
    System.exit(0);
  }
}
