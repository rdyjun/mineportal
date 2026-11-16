package com.mineportal.update;

import java.util.Optional;

/** Compares the running build with whatever {@link ReleaseSource} returns. */
public final class UpdateChecker {

  private final Optional<AppVersion> current;
  private final ReleaseSource source;
  private final InstallPlatform platform;
  private final String repo;

  public UpdateChecker(
      Optional<AppVersion> current, ReleaseSource source, InstallPlatform platform, String repo) {
    this.current = current == null ? Optional.empty() : current;
    this.source = source == null ? Optional::empty : source;
    this.platform = platform == null ? InstallPlatform.of("", "") : platform;
    this.repo = repo;
  }

  public UpdateCheck check() {
    if (current.isEmpty()) {
      return UpdateCheck.failed();
    }
    Optional<ReleaseInfo> release;
    try {
      release = source.fetchLatest();
    } catch (RuntimeException ex) {
      return UpdateCheck.failed();
    }
    if (release == null || release.isEmpty()) {
      return UpdateCheck.failed();
    }
    return UpdatePolicy.evaluate(current.get(), release.get(), platform, repo);
  }
}
