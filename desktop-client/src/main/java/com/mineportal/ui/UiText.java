package com.mineportal.ui;

import com.mineportal.update.UpdateSchedule;
import java.util.Locale;

/** Korean and English copy for the desktop shell. The website uses the same two languages. */
public final class UiText {

  private final boolean korean;

  private UiText(boolean korean) {
    this.korean = korean;
  }

  public static UiText system() {
    String override = System.getProperty("mineportal.lang", "");
    if (!override.isBlank()) {
      return forLanguage(override);
    }
    return forLanguage(Locale.getDefault().getLanguage());
  }

  public static UiText forLanguage(String language) {
    String value = language == null ? "" : language.toLowerCase(Locale.ROOT);
    return new UiText(value.startsWith("ko"));
  }

  public boolean korean() {
    return korean;
  }

  public String updateMessage(String current, String latest) {
    if (korean) {
      return "새 업데이트가 있습니다 (현재 " + current + " → 최신 " + latest + ")";
    }
    return "A new update is available (current " + current + " → latest " + latest + ")";
  }

  public String updateTitle() {
    return korean ? "새 업데이트가 있습니다" : "A new update is available";
  }

  public String updateAction() {
    return korean ? "업데이트" : "Update";
  }

  public String ignore() {
    return korean ? "다시 보지 않기" : "Don't show again";
  }

  public String ignoreDetail() {
    if (korean) {
      return "이 버전의 알림을 다시 보지 않습니다";
    }
    return "Don't show the notice for this version again";
  }

  public String close() {
    return korean ? "닫기" : "Close";
  }

  public String aboutButton() {
    return korean ? "정보" : "About";
  }

  public String aboutTitle() {
    return korean ? "마인포탈" : "MinePortal";
  }

  public String aboutSubtitle() {
    return korean ? "데스크톱 클라이언트" : "Desktop client";
  }

  public String sectionUpdate() {
    return korean ? "업데이트" : "Updates";
  }

  public String sectionInfo() {
    return korean ? "정보" : "Information";
  }

  public String sectionLinks() {
    return korean ? "링크" : "Links";
  }

  public String versionLabel() {
    return korean ? "버전" : "Version";
  }

  public String buildLabel() {
    return korean ? "빌드" : "Build";
  }

  public String repoLabel() {
    return korean ? "저장소" : "Repository";
  }

  public String checkForUpdates() {
    return korean ? "업데이트 확인" : "Check for updates";
  }

  public String checking() {
    return korean ? "확인 중…" : "Checking…";
  }

  public String notCheckedYet() {
    return korean ? "아직 확인하지 않았습니다." : "Not checked yet.";
  }

  public String upToDate(String version) {
    if (korean) {
      return "현재 최신 버전입니다 (" + version + ").";
    }
    return "You're up to date (" + version + ").";
  }

  public String checkFailed() {
    if (korean) {
      return "업데이트를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.";
    }
    return "Couldn't check for updates. Try again in a moment.";
  }

  public String updateFound(String version) {
    if (korean) {
      return "최신 버전은 " + version + "입니다.";
    }
    return "Version " + version + " is available.";
  }

  public String autoCheck() {
    return korean ? "자동으로 업데이트 확인" : "Check for updates automatically";
  }

  public String autoHelp() {
    long hours = UpdateSchedule.INTERVAL.toHours();
    if (korean) {
      return "켜 두면 앱을 실행할 때와 " + hours + "시간마다 확인합니다. 자동으로 받거나 설치하지는 않습니다.";
    }
    return "When enabled, MinePortal checks at startup and every "
        + hours
        + " hours. Nothing is downloaded or installed automatically.";
  }

  public String website() {
    return korean ? "웹사이트" : "Website";
  }

  public String releases() {
    return korean ? "릴리스" : "Releases";
  }

  public String source() {
    return korean ? "소스 코드" : "Source code";
  }
}
