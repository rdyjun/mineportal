package com.mineportal.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * Local desktop preferences. This file stores the automatic-check toggle and a dismissed version
 * only — never account, device, or Minecraft credentials.
 */
public final class DesktopSettings {

  static final int MAX_FILE_BYTES = 16_384;

  private final Path file;
  private boolean autoCheck = true;
  private String ignoredVersion = "";

  private DesktopSettings(Path file) {
    this.file = file;
  }

  public static DesktopSettings load(Path file) {
    DesktopSettings settings = new DesktopSettings(file);
    settings.read();
    return settings;
  }

  public synchronized boolean autoCheck() {
    return autoCheck;
  }

  public synchronized void setAutoCheck(boolean enabled) {
    autoCheck = enabled;
    write();
  }

  public synchronized Optional<AppVersion> ignoredVersion() {
    return AppVersion.parse(ignoredVersion);
  }

  public synchronized void setIgnoredVersion(AppVersion version) {
    ignoredVersion = version == null ? "" : version.toString();
    write();
  }

  private void read() {
    if (file == null) return;
    try {
      if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return;
      Properties properties = new Properties();
      try (InputStream in = Files.newInputStream(file)) {
        properties.load(in);
      }
      autoCheck = !"false".equalsIgnoreCase(properties.getProperty("autoCheck", "true").trim());
      String ignored = properties.getProperty("ignoredVersion", "");
      ignoredVersion = ignored == null ? "" : ignored.trim();
    } catch (IOException | IllegalArgumentException ex) {
      autoCheck = true;
      ignoredVersion = "";
    }
  }

  private void write() {
    if (file == null) return;
    Path tmp = null;
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Properties properties = new Properties();
      properties.setProperty("autoCheck", Boolean.toString(autoCheck));
      properties.setProperty("ignoredVersion", ignoredVersion);
      tmp =
          Files.createTempFile(
              parent == null ? Path.of(".") : parent, ".desktop-settings-", ".tmp");
      try (OutputStream out = Files.newOutputStream(tmp)) {
        properties.store(out, "MinePortal desktop preferences");
      }
      try {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        tmp = null;
      } catch (IOException atomicFailed) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        tmp = null;
      }
    } catch (IOException | SecurityException ex) {
      if (tmp != null) {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
          // The preferences stay in memory for this session.
        }
      }
    }
  }
}
