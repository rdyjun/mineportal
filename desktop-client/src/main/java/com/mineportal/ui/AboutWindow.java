package com.mineportal.ui;

import com.mineportal.BuildInfo;
import com.mineportal.update.AllowedLinks;
import com.mineportal.update.DesktopSettings;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** App info and a manual update check. Nothing here downloads or installs a build. */
final class AboutWindow {

  private final Stage stage = new Stage();
  private final Label title = new Label();
  private final Label subtitle = new Label();
  private final Label versionLabel = new Label();
  private final Label buildLabel = new Label();
  private final Label repoLabel = new Label();
  private final Label versionValue = new Label(BuildInfo.VERSION);
  private final Label buildValue = new Label(buildSummary());
  private final Label repoValue = new Label(BuildInfo.GITHUB_REPO);
  private final Button checkButton = new Button();
  private final Label status = new Label();
  private final CheckBox autoCheck = new CheckBox();
  private final Label help = new Label();
  private final Label websiteLabel = new Label();
  private final Label releasesLabel = new Label();
  private final Label sourceLabel = new Label();
  private final VBox links = new VBox(8);
  private UiText text;

  AboutWindow(
      Stage owner,
      UiText text,
      DesktopSettings settings,
      Consumer<String> openUrl,
      Runnable onCheck,
      Consumer<Boolean> onAutoCheck) {
    this.text = text;
    VBox root = new VBox();
    root.getStyleClass().add("about-root");
    root.setPrefWidth(460);

    title.getStyleClass().add("about-title");
    subtitle.getStyleClass().add("about-muted");
    versionValue.getStyleClass().add("about-value");
    buildValue.getStyleClass().add("about-value");
    repoValue.getStyleClass().add("about-value");
    help.getStyleClass().add("about-muted");
    help.setWrapText(true);
    status.setWrapText(true);
    status.setMinHeight(36);
    checkButton.getStyleClass().add("about-check");

    GridPane facts = new GridPane();
    facts.setHgap(16);
    facts.setVgap(8);
    ColumnConstraints labels = new ColumnConstraints();
    labels.setMinWidth(72);
    ColumnConstraints values = new ColumnConstraints();
    values.setHgrow(Priority.ALWAYS);
    facts.getColumnConstraints().addAll(labels, values);
    facts.add(versionLabel, 0, 0);
    facts.add(versionValue, 1, 0);
    facts.add(buildLabel, 0, 1);
    facts.add(buildValue, 1, 1);
    facts.add(repoLabel, 0, 2);
    facts.add(repoValue, 1, 2);

    checkButton.setOnAction(event -> onCheck.run());
    autoCheck.setSelected(settings.autoCheck());
    autoCheck.setOnAction(event -> onAutoCheck.accept(autoCheck.isSelected()));

    links.setFillWidth(true);
    addLink(websiteLabel, AllowedLinks.WEBSITE, openUrl);
    AllowedLinks.releases(BuildInfo.GITHUB_REPO)
        .ifPresent(url -> addLink(releasesLabel, url, openUrl));
    AllowedLinks.source(BuildInfo.GITHUB_REPO).ifPresent(url -> addLink(sourceLabel, url, openUrl));

    root.getChildren().addAll(title, subtitle, facts, checkButton, status, autoCheck, help);
    if (!links.getChildren().isEmpty()) {
      root.getChildren().add(new Separator());
      root.getChildren().add(links);
    }

    applyText(text);
    Scene scene = new Scene(root);
    scene.setFill(Color.web("#16161c"));
    scene.addEventHandler(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) stage.close();
        });
    ChromeTheme.apply(scene);
    stage.initOwner(owner);
    stage.setScene(scene);
    stage.setMinWidth(420);
    stage.setMinHeight(420);
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
    if (javaVersion.isEmpty() && fxVersion.isEmpty()) return "";
    if (fxVersion.isEmpty()) return "Java " + javaVersion;
    if (javaVersion.isEmpty()) return "JavaFX " + fxVersion;
    return "Java " + javaVersion + " · JavaFX " + fxVersion;
  }

  void show() {
    if (!stage.isShowing()) stage.show();
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
    status.setText(message == null ? "" : message);
    status.getStyleClass().removeAll("status-ok", "status-warn", "status-muted");
    if (styleClass != null && !styleClass.isBlank()) status.getStyleClass().add(styleClass);
  }

  void applyText(UiText next) {
    text = next;
    title.setText(next.aboutTitle());
    subtitle.setText(next.aboutSubtitle());
    versionLabel.setText(next.versionLabel());
    buildLabel.setText(next.buildLabel());
    repoLabel.setText(next.repoLabel());
    websiteLabel.setText(next.website());
    releasesLabel.setText(next.releases());
    sourceLabel.setText(next.source());
    autoCheck.setText(next.autoCheck());
    help.setText(next.autoHelp());
    if (!checkButton.isDisabled()) checkButton.setText(next.checkForUpdates());
    stage.setTitle(next.aboutTitle());
  }

  private void addLink(Label label, String url, Consumer<String> openUrl) {
    label.getStyleClass().add("about-muted");
    label.setMinWidth(72);
    Hyperlink link = new Hyperlink(url);
    link.setWrapText(true);
    link.setMaxWidth(320);
    link.setOnAction(event -> openUrl.accept(url));
    HBox row = new HBox(16, label, link);
    row.setPadding(new Insets(0));
    links.getChildren().add(row);
  }
}
