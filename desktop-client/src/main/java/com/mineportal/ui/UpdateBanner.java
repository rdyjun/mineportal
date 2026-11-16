package com.mineportal.ui;

import com.mineportal.update.UpdateOffer;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Bottom-right notice. It links to a release and never downloads or installs anything. */
final class UpdateBanner extends VBox {

  private static final double MESSAGE_WIDTH = 390;

  private final Label message = new Label();
  private final Button openButton = new Button();
  private final Button closeButton = new Button("×");
  private final Button ignoreButton = new Button();
  private final Consumer<UpdateOffer> onOpen;
  private final Consumer<UpdateOffer> onDismiss;
  private final Consumer<UpdateOffer> onIgnore;
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
    setPrefWidth(460);
    setMaxWidth(460);
    setMaxHeight(Region.USE_PREF_SIZE);

    message.getStyleClass().add("update-message");
    message.setWrapText(true);
    message.setMaxWidth(MESSAGE_WIDTH);
    message.setPrefWidth(MESSAGE_WIDTH);
    message.setCursor(Cursor.HAND);

    openButton.getStyleClass().add("banner-open");
    closeButton.getStyleClass().add("banner-close");
    ignoreButton.getStyleClass().add("banner-ignore");
    closeButton.setTooltip(new Tooltip(text.close()));
    closeButton.setAccessibleText(text.close());

    BorderPane header = new BorderPane();
    header.setCenter(message);
    header.setRight(closeButton);
    BorderPane.setAlignment(closeButton, Pos.TOP_RIGHT);
    BorderPane.setMargin(closeButton, new Insets(0, 0, 0, 8));

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox actions = new HBox(8, openButton, spacer, ignoreButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    getChildren().addAll(header, actions);
    setVisible(false);
    setManaged(false);
    setMouseTransparent(true);

    openButton.setOnAction(event -> open());
    closeButton.setOnAction(
        event -> {
          if (offer != null) onDismiss.accept(offer);
        });
    ignoreButton.setOnAction(
        event -> {
          if (offer != null) onIgnore.accept(offer);
        });
    setOnMouseClicked(
        event -> {
          if (insideControl(event.getTarget())) return;
          open();
        });
  }

  UpdateOffer offer() {
    return offer;
  }

  void setOffer(UpdateOffer offer, UiText text) {
    this.offer = offer;
    this.text = text;
    message.setText(text.updateMessage(offer.current().toString(), offer.latest().toString()));
    openButton.setText(offer.directDownload() ? text.download() : text.viewRelease());
    ignoreButton.setText(text.ignore());
    closeButton.setTooltip(new Tooltip(text.close()));
    closeButton.setAccessibleText(text.close());
    setShown(true);
  }

  void setShown(boolean shown) {
    setVisible(shown);
    setManaged(shown);
    setMouseTransparent(!shown);
  }

  private void open() {
    if (offer != null) onOpen.accept(offer);
  }

  private boolean insideControl(Object target) {
    Node node = target instanceof Node found ? found : null;
    while (node != null) {
      if (node == openButton || node == closeButton || node == ignoreButton) return true;
      node = node.getParent();
    }
    return false;
  }
}
