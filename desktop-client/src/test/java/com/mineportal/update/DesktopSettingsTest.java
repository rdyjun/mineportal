package com.mineportal.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopSettingsTest {

  @Test
  void defaultsToCheckingAndRemembersADismissedVersion(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("desktop-settings.properties");
    DesktopSettings settings = DesktopSettings.load(file);
    assertTrue(settings.autoCheck());
    assertTrue(settings.ignoredVersion().isEmpty());

    settings.setAutoCheck(false);
    settings.setIgnoredVersion(AppVersion.parse("1.2.0").orElseThrow());
    DesktopSettings loaded = DesktopSettings.load(file);
    assertFalse(loaded.autoCheck());
    assertEquals(AppVersion.parse("1.2.0").orElseThrow(), loaded.ignoredVersion().orElseThrow());
    String stored = Files.readString(file);
    assertFalse(stored.toLowerCase().contains("password"));
    assertFalse(stored.toLowerCase().contains("token"));

    try (Stream<Path> files = Files.list(dir)) {
      assertEquals(1, files.count());
    }
  }

  @Test
  void corruptOrHugeFilesDoNotChangeTheDefaults(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("desktop-settings.properties");
    Files.writeString(file, "%%%\u0000not properties");
    assertTrue(DesktopSettings.load(file).autoCheck());

    Files.write(file, new byte[DesktopSettings.MAX_FILE_BYTES + 1]);
    DesktopSettings huge = DesktopSettings.load(file);
    assertTrue(huge.autoCheck());
    assertTrue(huge.ignoredVersion().isEmpty());
  }

  @Test
  void sameVersionStaysDismissedUntilANewerOneExists() {
    AppVersion dismissed = AppVersion.parse("1.2.0").orElseThrow();
    UpdateOffer same =
        new UpdateOffer(
            AppVersion.parse("1.1.1").orElseThrow(),
            dismissed,
            "https://github.com/rdyjun/mineportal/releases/tag/1.2.0",
            null);
    UpdateCheck available = UpdateCheck.available(same);
    assertFalse(UpdateNotice.showAutomatically(available, null, Optional.of(dismissed)));
    assertFalse(UpdateNotice.showAutomatically(available, dismissed, Optional.empty()));
    assertTrue(
        UpdateNotice.showAutomatically(
            UpdateCheck.available(
                new UpdateOffer(
                    same.current(), AppVersion.parse("1.3.0").orElseThrow(), same.pageUrl(), null)),
            dismissed,
            Optional.of(dismissed)));
    assertFalse(UpdateNotice.showAutomatically(UpdateCheck.upToDate(), null, Optional.empty()));
    assertFalse(UpdateNotice.showAutomatically(UpdateCheck.failed(), null, Optional.empty()));
  }
}
