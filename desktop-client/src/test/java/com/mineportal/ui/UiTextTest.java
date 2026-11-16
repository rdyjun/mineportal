package com.mineportal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiTextTest {

  @Test
  void koreanUpdateCopyMatchesTheNotice() {
    UiText korean = UiText.forLanguage("ko-KR");
    assertTrue(korean.korean());
    assertEquals("새 업데이트가 있습니다 (현재 1.1.1 → 최신 1.2.0)", korean.updateMessage("1.1.1", "1.2.0"));
    assertEquals("새 업데이트가 있습니다", korean.updateTitle());
    assertEquals("업데이트", korean.updateAction());
    assertEquals("다시 보지 않기", korean.ignore());
    assertTrue(korean.autoHelp().contains("6"));
    assertTrue(korean.autoHelp().contains("설치하지는 않습니다"));
  }

  @Test
  void otherLanguagesUseEnglish() {
    UiText english = UiText.forLanguage("en");
    assertFalse(english.korean());
    assertEquals(
        "A new update is available (current 1.1.1 → latest 1.2.0)",
        english.updateMessage("1.1.1", "1.2.0"));
    assertEquals("Update", english.updateAction());
    assertEquals("Don't show again", english.ignore());
    assertFalse(UiText.forLanguage(null).korean());
    assertTrue(english.autoHelp().contains("6"));
    assertTrue(english.autoHelp().toLowerCase().contains("downloaded"));
  }

  @Test
  void systemPropertySelectsTheLanguage() {
    String previous = System.getProperty("mineportal.lang");
    try {
      System.setProperty("mineportal.lang", "en");
      assertFalse(UiText.system().korean());
      System.setProperty("mineportal.lang", "ko");
      assertTrue(UiText.system().korean());
    } finally {
      if (previous == null) {
        System.clearProperty("mineportal.lang");
      } else {
        System.setProperty("mineportal.lang", previous);
      }
    }
  }
}
