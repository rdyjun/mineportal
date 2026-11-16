package com.mineportal.ui;

import com.mineportal.update.UpdateOffer;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

/** Bottom-right toast. It links to a release and never downloads or installs anything. */
final class UpdateBanner extends HBox {

  private final Label title = new Label();
  private final Label currentChip = new Label();
  private final Label latestChip = new Label();
  private final Button openButton = new Button();
  private final Button closeButton = new Button("×");
  private final Button ignoreButton = new Button();
  private final Consumer<UpdateOffer> onOpen;
  private final Consumer<UpdateOffer> onDismiss;
  private final Consumer<UpdateOffer> onIgnore;
  private ParallelTransition motion;
  private UpdateOffer offer;
  private UiText text;

  UpdateBanner(
      UiText text,
      Consumer<UpdateOffer> onOpen,
      Consumer<UpdateOffer> onDismiss,
      Consumer<UpdateOffer> onIgnore) {
    this.text = text;
    this.onOpen = onOpen;
    this.onDismiss = onDismiss;
    this.onIgnore = onIgnore;
    getStyleClass().add("update-banner");
    setAlignment(Pos.TOP_LEFT);
    setSpacing(12);
    setPrefWidth(400);
    setMaxWidth(400);
    setMaxHeight(Region.USE_PREF_SIZE);
    setCursor(Cursor.HAND);

    StackIcon icon = new StackIcon();
    VBox column = new VBox(10, header(), chips(), actions());
    HBox.setHgrow(column, Priority.ALWAYS);
    getChildren().addAll(icon, column);

    setVisible(false);
    setManaged(false);
    setMouseTransparent(true);

    openButton.setOnAction(event -> open());
    closeButton.setOnAction(
        event -> {
          if (offer != null) {
            onDismiss.accept(offer);
          }
        });
    ignoreButton.setOnAction(
        event -> {
          if (offer != null) {
            onIgnore.accept(offer);
          }
        });
    setOnMouseClicked(
        event -> {
          if (insideControl(event.getTarget())) {
            return;
          }
          open();
        });
  }

  UpdateOffer offer() {
    return offer;
  }

  void setOffer(UpdateOffer offer, UiText text) {
    this.offer = offer;
    this.text = text;
    title.setText(text.updateTitle());
    currentChip.setText(offer.current().toString());
    latestChip.setText(offer.latest().toString());
    openButton.setText(text.updateAction());
    ignoreButton.setText(text.ignore());
    ignoreButton.setTooltip(new Tooltip(text.ignoreDetail()));
    ignoreButton.setAccessibleText(text.ignoreDetail());
    closeButton.setTooltip(new Tooltip(text.close()));
    closeButton.setAccessibleText(text.close());
    setAccessibleText(text.updateMessage(offer.current().toString(), offer.latest().toString()));
    setShown(true);
  }

  void setShown(boolean shown) {
    stopMotion();
    setVisible(shown);
    setManaged(shown);
    setMouseTransparent(!shown);
    if (shown) {
      playIn();
    } else {
      setOpacity(1);
      setTranslateY(0);
    }
  }

  /** Freezes the intro so a snapshot sees the card fully on screen. */
  void settle() {
    stopMotion();
    setOpacity(1);
    setTranslateY(0);
  }

  private HBox header() {
    title.getStyleClass().add("banner-title");
    title.setWrapText(true);
    title.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(title, Priority.ALWAYS);
    closeButton.getStyleClass().add("icon-button");
    closeButton.setMnemonicParsing(false);
    HBox row = new HBox(8, title, closeButton);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private HBox chips() {
    currentChip.getStyleClass().add("version-chip");
    latestChip.getStyleClass().addAll("version-chip", "version-chip-accent");
    Label arrow = new Label("→");
    arrow.getStyleClass().add("chip-arrow");
    HBox row = new HBox(6, currentChip, arrow, latestChip);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private HBox actions() {
    openButton.getStyleClass().add("primary-button");
    ignoreButton.getStyleClass().add("ghost-button");
    openButton.setMnemonicParsing(false);
    ignoreButton.setMnemonicParsing(false);
    HBox row = new HBox(8, openButton, ignoreButton);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private void playIn() {
    setOpacity(0);
    setTranslateY(16);
    FadeTransition fade = new FadeTransition(Duration.millis(220), this);
    fade.setFromValue(0);
    fade.setToValue(1);
    fade.setInterpolator(Interpolator.EASE_OUT);
    TranslateTransition slide = new TranslateTransition(Duration.millis(260), this);
    slide.setFromY(16);
    slide.setToY(0);
    slide.setInterpolator(Interpolator.EASE_OUT);
    motion = new ParallelTransition(fade, slide);
    motion.play();
  }

  private void stopMotion() {
    if (motion != null) {
      motion.stop();
      motion = null;
    }
  }

  private void open() {
    if (offer != null) {
      onOpen.accept(offer);
    }
  }

  private boolean insideControl(Object target) {
    Node node = target instanceof Node found ? found : null;
    while (node != null) {
      if (node == openButton || node == closeButton || node == ignoreButton) {
        return true;
      }
      node = node.getParent();
    }
    return false;
  }

  /** Accent tile with an up arrow drawn as geometry, so it does not depend on a font. */
  private static final class StackIcon extends StackPane {
    private StackIcon() {
      getStyleClass().add("banner-icon");
      setMinSize(36, 36);
      setMaxSize(36, 36);
      Polygon arrow = new Polygon(10, 1, 18, 10, 13.5, 10, 13.5, 19, 6.5, 19, 6.5, 10, 2, 10);
      arrow.getStyleClass().add("banner-arrow");
      arrow.setMouseTransparent(true);
      getChildren().add(arrow);
    }
  }
}
