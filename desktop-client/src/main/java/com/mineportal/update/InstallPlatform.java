package com.mineportal.update;

import java.util.Locale;
import java.util.Optional;

/**
 * Installer file published by the desktop packaging workflow. Intel Macs and other operating
 * systems have no installer; callers then open the release page instead of a download.
 */
public final class InstallPlatform {

  public static final String WINDOWS_ZIP = "MinePortal-win.zip";
  public static final String MAC_ARM_DMG = "MinePortal-mac-arm64.dmg";

  private final String osName;
  private final String osArch;

  private InstallPlatform(String osName, String osArch) {
    this.osName = osName == null ? "" : osName;
    this.osArch = osArch == null ? "" : osArch;
  }

  public static InstallPlatform detect() {
    return of(System.getProperty("os.name"), System.getProperty("os.arch"));
  }

  public static InstallPlatform of(String osName, String osArch) {
    return new InstallPlatform(osName, osArch);
  }

  public Optional<String> installerFileName() {
    String os = osName.toLowerCase(Locale.ROOT);
    String arch = osArch.toLowerCase(Locale.ROOT);
    // "darwin" contains the letters "win", so match the Windows token itself.
    if (os.contains("windows")) {
      return Optional.of(WINDOWS_ZIP);
    }
    if (os.contains("mac") || os.contains("darwin")) {
      if (arch.contains("aarch64") || "arm64".equals(arch) || arch.startsWith("arm")) {
        return Optional.of(MAC_ARM_DMG);
      }
      return Optional.empty();
    }
    return Optional.empty();
  }
}
