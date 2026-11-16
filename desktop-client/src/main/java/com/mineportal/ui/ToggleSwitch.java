package com.mineportal.ui;

import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Pill switch used by the about window. Color and the knob live in the stylesheet. */
final class ToggleSwitch extends StackPane {

  private final Region knob = new Region();
  private final Consumer<Boolean> onChange;
  private boolean selected;

  ToggleSwitch(boolean selected, Consumer<Boolean> onChange) {
    this.onChange = onChange == null ? value -> {} : onChange;
    getStyleClass().add("toggle");
    knob.getStyleClass().add("toggle-knob");
    getChildren().add(knob);
    setMinSize(42, 24);
    setPrefSize(42, 24);
    setMaxSize(42, 24);
    setFocusTraversable(true);
    setCursor(Cursor.HAND);
    show(selected, false);
    setOnMouseClicked(
        event -> {
          flip();
          event.consume();
        });
    setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
            flip();
            event.consume();
          }
        });
  }

  private void flip() {
    show(!selected, true);
  }

  private void show(boolean value, boolean notify) {
    selected = value;
    setAlignment(value ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    if (value) {
      if (!getStyleClass().contains("on")) {
        getStyleClass().add("on");
      }
    } else {
      getStyleClass().remove("on");
    }
    if (notify) {
      onChange.accept(value);
    }
  }
}
