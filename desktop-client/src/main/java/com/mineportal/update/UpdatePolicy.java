package com.mineportal.update;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Decides whether a GitHub release is a stable update for this build. A missing platform installer
 * still counts: the click target becomes that release's page, never the generic {@code
 * releases/latest/download} URL, which 404s when an asset was not published.
 */
public final class UpdatePolicy {

  private UpdatePolicy() {}

  public static UpdateCheck evaluate(
      AppVersion current, ReleaseInfo release, InstallPlatform platform, String repo) {
    if (current == null || release == null || platform == null || !SafeGithubUrl.validRepo(repo)) {
      return UpdateCheck.failed();
    }
    if (release.draft() || release.prerelease()) {
      return UpdateCheck.upToDate();
    }
    Optional<AppVersion> parsed = AppVersion.parse(release.tagName());
    if (parsed.isEmpty()) {
      return UpdateCheck.failed();
    }
    AppVersion latest = parsed.get();
    if (!latest.stable() || !latest.newerThan(current)) {
      return UpdateCheck.upToDate();
    }

    String page = pageUrl(release, repo);
    String asset = assetUrl(release, platform, repo);
    if (page == null && asset == null) {
      return UpdateCheck.failed();
    }
    return UpdateCheck.available(new UpdateOffer(current, latest, page, asset));
  }

  private static String pageUrl(ReleaseInfo release, String repo) {
    if (SafeGithubUrl.isRepoReleasePage(release.htmlUrl(), repo)) {
      return release.htmlUrl();
    }
    return fallbackPage(repo, release.tagName()).orElse(null);
  }

  private static Optional<String> fallbackPage(String repo, String tag) {
    if (tag == null) {
      return Optional.empty();
    }
    String trimmed = tag.trim();
    if (!trimmed.matches("[A-Za-z0-9._+-]{1,64}") || trimmed.contains("..")) {
      return Optional.empty();
    }
    String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("+", "%2B");
    String url = "https://github.com/" + repo + "/releases/tag/" + encoded;
    return SafeGithubUrl.isRepoReleasePage(url, repo) ? Optional.of(url) : Optional.empty();
  }

  private static String assetUrl(ReleaseInfo release, InstallPlatform platform, String repo) {
    Optional<String> expected = platform.installerFileName();
    if (expected.isEmpty()) {
      return null;
    }
    String name = expected.get();
    for (ReleaseInfo.Asset asset : release.assets()) {
      if (!name.equals(asset.name())) {
        continue;
      }
      if (SafeGithubUrl.isRepoAsset(asset.downloadUrl(), repo, name)) {
        return asset.downloadUrl();
      }
    }
    return null;
  }
}
