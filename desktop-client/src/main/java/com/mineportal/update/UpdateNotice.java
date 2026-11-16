package com.mineportal.update;

import java.util.Optional;

/** Whether an automatic check should put the banner back on screen. */
public final class UpdateNotice {

  private UpdateNotice() {}

  public static boolean showAutomatically(
      UpdateCheck check, AppVersion sessionDismissed, Optional<AppVersion> ignored) {
    if (check == null
        || check.status() != UpdateCheck.Status.UPDATE_AVAILABLE
        || check.offer() == null) {
      return false;
    }
    AppVersion latest = check.offer().latest();
    if (sessionDismissed != null && sessionDismissed.compareTo(latest) == 0) {
      return false;
    }
    return ignored == null || ignored.filter(version -> version.compareTo(latest) == 0).isEmpty();
  }
}
