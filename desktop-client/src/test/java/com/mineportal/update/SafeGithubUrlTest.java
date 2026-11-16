package com.mineportal.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafeGithubUrlTest {

  private static final String REPO = "rdyjun/mineportal";

  @Test
  void acceptsOnlyThisRepositorysHttpsReleaseUrls() {
    assertTrue(SafeGithubUrl.validRepo(REPO));
    assertFalse(SafeGithubUrl.validRepo("../evil"));
    assertFalse(SafeGithubUrl.validRepo("rdyjun/../mineportal"));
    assertFalse(SafeGithubUrl.validRepo("rdyjun/mineportal/extra"));
    assertFalse(SafeGithubUrl.validRepo("https://github.com/rdyjun/mineportal"));

    String page = "https://github.com/rdyjun/mineportal/releases/tag/1.0.0";
    String zip = "https://github.com/rdyjun/mineportal/releases/download/1.0.0/MinePortal-win.zip";
    assertTrue(SafeGithubUrl.isRepoReleasePage(page, REPO));
    assertTrue(SafeGithubUrl.isRepoAsset(zip, REPO, InstallPlatform.WINDOWS_ZIP));
    assertTrue(SafeGithubUrl.isRepoHome("https://github.com/rdyjun/mineportal", REPO));
    assertTrue(AllowedLinks.isOpenable(AllowedLinks.WEBSITE, REPO));
    assertTrue(AllowedLinks.isOpenable(page, REPO));
    assertTrue(AllowedLinks.isOpenable(zip, REPO));
  }

  @Test
  void rejectsBrokenLatestAliasAndForeignUrls() {
    String latestDmg =
        "https://github.com/rdyjun/mineportal/releases/latest/download/MinePortal-mac-arm64.dmg";
    assertFalse(SafeGithubUrl.isRepoAsset(latestDmg, REPO, InstallPlatform.MAC_ARM_DMG));
    assertFalse(SafeGithubUrl.isRepoReleasePage(latestDmg, REPO));
    assertFalse(AllowedLinks.isOpenable(latestDmg, REPO));
    assertFalse(
        AllowedLinks.isOpenable(
            "https://github.com/rdyjun/mineportal/releases/download/1.0.0/mineportal-client.jar",
            REPO));
    assertFalse(AllowedLinks.isOpenable("https://github.com/other/mineportal/releases", REPO));
    assertFalse(AllowedLinks.isOpenable("http://github.com/rdyjun/mineportal/releases", REPO));
    assertFalse(
        AllowedLinks.isOpenable("https://user@github.com/rdyjun/mineportal/releases", REPO));
    assertFalse(
        AllowedLinks.isOpenable("https://github.com/rdyjun/mineportal/releases?redirect=1", REPO));
    assertFalse(AllowedLinks.isOpenable("https://github.com.evil/rdyjun/mineportal", REPO));
    assertFalse(AllowedLinks.isOpenable("https://mineportal.kr.evil/", REPO));
    assertFalse(AllowedLinks.isOpenable("https://mineportal.kr/account", REPO));
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases", AllowedLinks.releases(REPO).orElseThrow());
    assertTrue(AllowedLinks.releases("not a repo").isEmpty());
  }
}
