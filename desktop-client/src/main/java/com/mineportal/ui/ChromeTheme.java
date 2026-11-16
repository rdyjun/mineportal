package com.mineportal.ui;

import java.net.URL;
import javafx.scene.Scene;

final class ChromeTheme {

  private ChromeTheme() {}

  static void apply(Scene scene) {
    if (scene == null) {
      return;
    }
    URL resource = ChromeTheme.class.getResource("desktop-chrome.css");
    if (resource == null) {
      return;
    }
    String url = resource.toExternalForm();
    if (!scene.getStylesheets().contains(url)) {
      scene.getStylesheets().add(url);
    }
  }
}
