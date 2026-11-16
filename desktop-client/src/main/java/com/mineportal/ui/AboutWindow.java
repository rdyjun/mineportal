package com.mineportal.ui;

import com.mineportal.BuildInfo;
import com.mineportal.update.AllowedLinks;
import com.mineportal.update.DesktopSettings;
import java.net.URI;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** App info and a manual update check. Nothing here downloads or installs a build. */
final class AboutWindow {

  private final Stage stage = new Stage();
  private final Label name = new Label();
  private final Label subtitle = new Label();
  private final Label versionChip = new Label();
  private final Label updateHeading = new Label();
  private final Label infoHeading = new Label();
  private final Label linksHeading = new Label();
  private final Region statusDot = new Region();
  private final Label status = new Label();
  private final Button checkButton = new Button();
  private final Label autoLabel = new Label();
  private final ToggleSwitch autoToggle;
  private final Label help = new Label();
  private final Label versionLabel = new Label();
  private final Label buildLabel = new Label();
  private final Label repoLabel = new Label();
  private final Label versionValue = new Label(BuildInfo.VERSION);
  private final Label buildValue = new Label(buildSummary());
  private final Label repoValue = new Label(BuildInfo.GITHUB_REPO);
  private final Label websiteLabel = new Label();
  private final Label releasesLabel = new Label();
  private final Label sourceLabel = new Label();
  private final Button closeButton = new Button("×");
  private final VBox linkList = new VBox(2);
  private UiText text;
  private boolean statusIsPlaceholder = true;
  private double dragX;
  private double dragY;

  AboutWindow(
      Stage owner,
      UiText text,
      DesktopSettings settings,
      Consumer<String> openUrl,
      Runnable onCheck,
      Consumer<Boolean> onAutoCheck) {
    this.text = text;
    this.autoToggle = new ToggleSwitch(settings.autoCheck(), onAutoCheck);
    VBox body = new VBox(16);
    body.getStyleClass().add("about-content");
    body.getChildren().addAll(header(), updateCard(onCheck), infoCard());
    addLink(websiteLabel, AllowedLinks.WEBSITE, openUrl);
    AllowedLinks.releases(BuildInfo.GITHUB_REPO)
        .ifPresent(url -> addLink(releasesLabel, url, openUrl));
    AllowedLinks.source(BuildInfo.GITHUB_REPO).ifPresent(url -> addLink(sourceLabel, url, openUrl));
    if (!linkList.getChildren().isEmpty()) {
      linksHeading.getStyleClass().add("section-title");
      body.getChildren().add(section(linksHeading, linkList));
    }

    VBox window = new VBox(chrome(), body);
    window.getStyleClass().add("about-window");
    window.setPrefWidth(520);
    window.setMinWidth(520);
    window.setMaxWidth(520);

    applyText(text);
    Scene scene = new Scene(window);
    scene.setFill(Color.web("#1e1f22"));
    scene.addEventHandler(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            stage.close();
          }
        });
    ChromeTheme.apply(scene);
    stage.initStyle(StageStyle.UNDECORATED);
    stage.initOwner(owner);
    stage.setResizable(false);
    stage.setScene(scene);
    stage.setOnShown(
        event -> {
          stage.sizeToScene();
          stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
          stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        });
  }

  static String buildSummary() {
    String javaVersion = System.getProperty("java.version", "").trim();
    String fxVersion = System.getProperty("javafx.version", "").trim();
    if (javaVersion.isEmpty() && fxVersion.isEmpty()) {
      return "";
    }
    if (fxVersion.isEmpty()) {
      return "Java " + javaVersion;
    }
    if (javaVersion.isEmpty()) {
      return "JavaFX " + fxVersion;
    }
    return "Java " + javaVersion + " · JavaFX " + fxVersion;
  }

  void show() {
    if (!stage.isShowing()) {
      stage.show();
    }
    stage.toFront();
  }

  Stage stage() {
    return stage;
  }

  void setChecking(boolean checking) {
    checkButton.setDisable(checking);
    checkButton.setText(checking ? text.checking() : text.checkForUpdates());
  }

  void setStatus(String message, String styleClass) {
    statusIsPlaceholder = false;
    status.setText(message == null ? "" : message);
    status.getStyleClass().removeAll("status-ok", "status-warn", "status-muted");
    statusDot.getStyleClass().removeAll("dot-ok", "dot-warn", "dot-muted");
    if ("status-ok".equals(styleClass)) {
      status.getStyleClass().add("status-ok");
      statusDot.getStyleClass().add("dot-ok");
    } else if ("status-warn".equals(styleClass)) {
      status.getStyleClass().add("status-warn");
      statusDot.getStyleClass().add("dot-warn");
    } else {
      status.getStyleClass().add("status-muted");
      statusDot.getStyleClass().add("dot-muted");
    }
  }

  void applyText(UiText next) {
    text = next;
    name.setText(next.aboutTitle());
    subtitle.setText(next.aboutSubtitle());
    updateHeading.setText(next.sectionUpdate());
    infoHeading.setText(next.sectionInfo());
    linksHeading.setText(next.sectionLinks());
    versionLabel.setText(next.versionLabel());
    buildLabel.setText(next.buildLabel());
    repoLabel.setText(next.repoLabel());
    websiteLabel.setText(next.website());
    releasesLabel.setText(next.releases());
    sourceLabel.setText(next.source());
    autoLabel.setText(next.autoCheck());
    autoToggle.setAccessibleText(next.autoCheck());
    help.setText(next.autoHelp());
    closeButton.setAccessibleText(next.close());
    closeButton.setTooltip(new Tooltip(next.close()));
    if (!checkButton.isDisabled()) {
      checkButton.setText(next.checkForUpdates());
    }
    stage.setTitle(next.aboutTitle());
    if (statusIsPlaceholder) {
      setStatus(next.notCheckedYet(), "status-muted");
      statusIsPlaceholder = true;
    }
  }

  private HBox chrome() {
    closeButton.getStyleClass().add("icon-button");
    closeButton.setMnemonicParsing(false);
    closeButton.setOnAction(event -> stage.close());
    Region drag = new Region();
    HBox.setHgrow(drag, Priority.ALWAYS);
    drag.setMinHeight(28);
    HBox bar = new HBox(drag, closeButton);
    bar.getStyleClass().add("window-chrome");
    bar.setAlignment(Pos.CENTER_RIGHT);
    bar.setOnMousePressed(
        event -> {
          if (inside(event.getTarget(), closeButton)) {
            return;
          }
          dragX = event.getScreenX() - stage.getX();
          dragY = event.getScreenY() - stage.getY();
        });
    bar.setOnMouseDragged(
        event -> {
          if (inside(event.getTarget(), closeButton)) {
            return;
          }
          stage.setX(event.getScreenX() - dragX);
          stage.setY(event.getScreenY() - dragY);
        });
    return bar;
  }

  private HBox header() {
    StackPane logo = new StackPane();
    logo.getStyleClass().add("app-logo");
    logo.setMinSize(48, 48);
    logo.setMaxSize(48, 48);
    Label mark = new Label("M");
    mark.getStyleClass().add("app-logo-mark");
    logo.getChildren().add(mark);

    name.getStyleClass().add("about-name");
    subtitle.getStyleClass().add("about-subtitle");
    versionChip.getStyleClass().addAll("version-chip", "header-chip");
    versionChip.setText("v" + BuildInfo.VERSION);
    VBox titles = new VBox(4, name, subtitle, versionChip);
    titles.setAlignment(Pos.CENTER_LEFT);
    HBox row = new HBox(14, logo, titles);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private VBox updateCard(Runnable onCheck) {
    updateHeading.getStyleClass().add("section-title");
    status.setWrapText(true);
    statusDot.getStyleClass().add("status-dot");
    statusDot.setMinSize(8, 8);
    statusDot.setPrefSize(8, 8);
    statusDot.setMaxSize(8, 8);
    HBox.setHgrow(status, Priority.ALWAYS);
    HBox statusRow = new HBox(8, statusDot, status);
    statusRow.setAlignment(Pos.CENTER_LEFT);

    checkButton.getStyleClass().add("primary-button");
    checkButton.setMnemonicParsing(false);
    checkButton.setOnAction(event -> onCheck.run());

    autoLabel.setWrapText(true);
    HBox.setHgrow(autoLabel, Priority.ALWAYS);
    HBox toggleRow = new HBox(12, autoLabel, autoToggle);
    toggleRow.setAlignment(Pos.CENTER_LEFT);

    help.getStyleClass().add("about-help");
    help.setWrapText(true);
    return section(updateHeading, statusRow, checkButton, toggleRow, help);
  }

  private VBox infoCard() {
    infoHeading.getStyleClass().add("section-title");
    return section(
        infoHeading,
        infoRow(versionLabel, versionValue),
        infoRow(buildLabel, buildValue),
        infoRow(repoLabel, repoValue));
  }

  private static VBox section(Node... nodes) {
    VBox card = new VBox(10);
    card.getStyleClass().add("section-card");
    card.getChildren().addAll(nodes);
    return card;
  }

  private static HBox infoRow(Label label, Label value) {
    label.getStyleClass().add("row-label");
    value.getStyleClass().add("row-value");
    value.setWrapText(true);
    HBox.setHgrow(value, Priority.ALWAYS);
    HBox row = new HBox(12, label, value);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private void addLink(Label label, String url, Consumer<String> openUrl) {
    label.getStyleClass().add("link-name");
    Label dest = new Label(shortLink(url));
    dest.getStyleClass().add("link-dest");
    Label chevron = new Label("›");
    chevron.getStyleClass().add("link-chevron");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(10, label, spacer, dest, chevron);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPrefWidth(430);
    Button button = new Button();
    button.setGraphic(row);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setAlignment(Pos.CENTER_LEFT);
    button.setMaxWidth(Double.MAX_VALUE);
    button.getStyleClass().add("link-row");
    button.setMnemonicParsing(false);
    button.setOnAction(event -> openUrl.accept(url));
    linkList.getChildren().add(button);
  }

  private static String shortLink(String url) {
    try {
      URI uri = URI.create(url);
      String host = uri.getHost() == null ? url : uri.getHost();
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      if (path.isEmpty()) {
        return host;
      }
      return host + path;
    } catch (IllegalArgumentException ex) {
      return url;
    }
  }

  private static boolean inside(Object target, Node control) {
    Node node = target instanceof Node found ? found : null;
    while (node != null) {
      if (node == control) {
        return true;
      }
      node = node.getParent();
    }
    return false;
  }
}
