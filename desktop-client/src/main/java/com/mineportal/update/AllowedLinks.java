package com.mineportal.update;

import java.util.Optional;

/** Links the desktop shell is willing to open. Anything else is dropped. */
public final class AllowedLinks {

  public static final String WEBSITE = "https://mineportal.kr/";

  private AllowedLinks() {}

  public static Optional<String> releases(String repo) {
    if (!SafeGithubUrl.validRepo(repo)) return Optional.empty();
    String url = "https://github.com/" + repo + "/releases";
    return SafeGithubUrl.isRepoReleasePage(url, repo) ? Optional.of(url) : Optional.empty();
  }

  public static Optional<String> source(String repo) {
    if (!SafeGithubUrl.validRepo(repo)) return Optional.empty();
    String url = "https://github.com/" + repo;
    return SafeGithubUrl.isRepoHome(url, repo) ? Optional.of(url) : Optional.empty();
  }

  public static boolean isOpenable(String url, String repo) {
    if (WEBSITE.equals(url)) return true;
    if (SafeGithubUrl.isRepoHome(url, repo)) return true;
    if (SafeGithubUrl.isRepoReleasePage(url, repo)) return true;
    if (SafeGithubUrl.isRepoAsset(url, repo, InstallPlatform.WINDOWS_ZIP)) return true;
    if (SafeGithubUrl.isRepoAsset(url, repo, InstallPlatform.MAC_ARM_DMG)) return true;
    return false;
  }
}
