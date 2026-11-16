package com.mineportal.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdatePolicyTest {

  private static final String REPO = "rdyjun/mineportal";
  private static final AppVersion CURRENT = AppVersion.parse("1.1.1").orElseThrow();

  @Test
  void publishedOneZeroIsNotAnUpdateAndHasNoMacInstaller() {
    UpdateCheck check =
        UpdatePolicy.evaluate(
            CURRENT,
            release(PUBLISHED_1_0_0).orElseThrow(),
            InstallPlatform.of("Mac OS X", "aarch64"),
            REPO);
    assertEquals(UpdateCheck.Status.UP_TO_DATE, check.status());
    assertNull(check.offer());
  }

  @Test
  void newerWindowsReleaseLinksTheZipFromThatRelease() {
    UpdateCheck check =
        UpdatePolicy.evaluate(
            CURRENT,
            release(NEWER_WITH_BOTH).orElseThrow(),
            InstallPlatform.of("Windows 11", "amd64"),
            REPO);
    assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE, check.status());
    assertTrue(check.offer().directDownload());
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases/download/1.2.0/MinePortal-win.zip",
        check.offer().openUrl());
    assertFalse(check.offer().openUrl().contains("/releases/latest/"));
  }

  @Test
  void newerMacReleaseWithoutDmgOpensTheReleasePage() {
    UpdateCheck check =
        UpdatePolicy.evaluate(
            CURRENT,
            release(
                    PUBLISHED_1_0_0
                        .replace("\"tag_name\": \"1.0.0\"", "\"tag_name\": \"1.2.0\"")
                        .replace("/1.0.0/", "/1.2.0/")
                        .replace("/tag/1.0.0", "/tag/1.2.0"))
                .orElseThrow(),
            InstallPlatform.of("Mac OS X", "aarch64"),
            REPO);
    assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE, check.status());
    assertFalse(check.offer().directDownload());
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases/tag/1.2.0", check.offer().openUrl());
    assertFalse(check.offer().openUrl().contains("MinePortal-mac-arm64.dmg"));
    assertFalse(check.offer().openUrl().contains("/releases/latest/"));
  }

  @Test
  void macArmDmgIsUsedOnlyWhenThatAssetExists() {
    UpdateCheck arm =
        UpdatePolicy.evaluate(
            CURRENT,
            release(NEWER_WITH_BOTH).orElseThrow(),
            InstallPlatform.of("Mac OS X", "aarch64"),
            REPO);
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases/download/1.2.0/MinePortal-mac-arm64.dmg",
        arm.offer().openUrl());

    UpdateCheck intel =
        UpdatePolicy.evaluate(
            CURRENT,
            release(NEWER_WITH_BOTH).orElseThrow(),
            InstallPlatform.of("Mac OS X", "x86_64"),
            REPO);
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases/tag/1.2.0", intel.offer().openUrl());

    UpdateCheck linux =
        UpdatePolicy.evaluate(
            CURRENT,
            release(NEWER_WITH_BOTH).orElseThrow(),
            InstallPlatform.of("Linux", "amd64"),
            REPO);
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases/tag/1.2.0", linux.offer().openUrl());
  }

  @Test
  void prereleaseDraftAndBadTagsAreNotOffered() {
    assertEquals(
        UpdateCheck.Status.UP_TO_DATE,
        UpdatePolicy.evaluate(CURRENT, release(prerelease("1.3.0")).orElseThrow(), windows(), REPO)
            .status());
    assertEquals(
        UpdateCheck.Status.UP_TO_DATE,
        UpdatePolicy.evaluate(
                CURRENT, release(stableTag("1.3.0-rc.1", false)).orElseThrow(), windows(), REPO)
            .status());
    ReleaseInfo draft =
        release(NEWER_WITH_BOTH.replace("\"draft\": false", "\"draft\": true")).orElseThrow();
    assertEquals(
        UpdateCheck.Status.UP_TO_DATE,
        UpdatePolicy.evaluate(CURRENT, draft, windows(), REPO).status());
    assertEquals(
        UpdateCheck.Status.CHECK_FAILED,
        UpdatePolicy.evaluate(
                CURRENT, release(stableTag("latest", false)).orElseThrow(), windows(), REPO)
            .status());
    assertEquals(
        UpdateCheck.Status.UP_TO_DATE,
        UpdatePolicy.evaluate(
                CURRENT, release(stableTag("1.1.1", false)).orElseThrow(), windows(), REPO)
            .status());
    assertEquals(
        UpdateCheck.Status.UPDATE_AVAILABLE,
        UpdatePolicy.evaluate(
                AppVersion.parse("1.2.0-beta.1").orElseThrow(),
                release(stableTag("1.2.0", false)).orElseThrow(),
                windows(),
                REPO)
            .status());
  }

  @Test
  void unsafePageFallsBackToTheTagPageAndDropsBadAssets() {
    String json =
        """
        {
          "tag_name": "1.4.0",
          "draft": false,
          "prerelease": false,
          "html_url": "http://evil.example/phish",
          "assets": [
            {
              "name": "MinePortal-win.zip",
              "browser_download_url": "https://evil.example/MinePortal-win.zip"
            }
          ]
        }
        """;
    UpdateCheck check =
        UpdatePolicy.evaluate(CURRENT, release(json).orElseThrow(), windows(), REPO);
    assertEquals(UpdateCheck.Status.UPDATE_AVAILABLE, check.status());
    assertEquals(
        "https://github.com/rdyjun/mineportal/releases/tag/1.4.0", check.offer().openUrl());
    assertFalse(check.offer().directDownload());
  }

  @Test
  void rejectsNonBooleanReleaseFlagsAndRateLimitBodies() {
    assertTrue(ReleaseInfo.parse("{\"message\":\"API rate limit exceeded\"}").isEmpty());
    assertTrue(ReleaseInfo.parse("not-json").isEmpty());
    assertTrue(ReleaseInfo.parse("[]").isEmpty());
    assertTrue(
        ReleaseInfo.parse(
                """
                {"tag_name":"1.2.0","draft":false,"prerelease":"true","html_url":"https://github.com/rdyjun/mineportal/releases/tag/1.2.0","assets":[]}
                """)
            .isEmpty());
  }

  @Test
  void installerNamesFollowThePackagedFiles() {
    assertEquals(
        InstallPlatform.WINDOWS_ZIP,
        InstallPlatform.of("Windows 10", "amd64").installerFileName().orElseThrow());
    assertEquals(
        InstallPlatform.MAC_ARM_DMG,
        InstallPlatform.of("Mac OS X", "aarch64").installerFileName().orElseThrow());
    assertEquals(
        InstallPlatform.MAC_ARM_DMG,
        InstallPlatform.of("Darwin", "arm64").installerFileName().orElseThrow());
    assertTrue(InstallPlatform.of("Mac OS X", "x86_64").installerFileName().isEmpty());
    assertTrue(InstallPlatform.of("Linux", "amd64").installerFileName().isEmpty());
  }

  private static InstallPlatform windows() {
    return InstallPlatform.of("Windows 11", "amd64");
  }

  private static java.util.Optional<ReleaseInfo> release(String json) {
    return ReleaseInfo.parse(json);
  }

  private static String prerelease(String tag) {
    return stableTag(tag, true);
  }

  private static String stableTag(String tag, boolean prerelease) {
    return """
        {
          "tag_name": "%s",
          "draft": false,
          "prerelease": %s,
          "html_url": "https://github.com/rdyjun/mineportal/releases/tag/%s",
          "assets": [
            {
              "name": "MinePortal-win.zip",
              "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/%s/MinePortal-win.zip"
            }
          ]
        }
        """
        .formatted(tag, prerelease, tag, tag);
  }

  private static final String PUBLISHED_1_0_0 =
      """
      {
        "tag_name": "1.0.0",
        "name": "MinePortal Client 1.0.0",
        "draft": false,
        "prerelease": false,
        "html_url": "https://github.com/rdyjun/mineportal/releases/tag/1.0.0",
        "assets": [
          {
            "name": "mineportal-client.jar",
            "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.0.0/mineportal-client.jar"
          },
          {
            "name": "MinePortal-win.zip",
            "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.0.0/MinePortal-win.zip"
          }
        ]
      }
      """;

  private static final String NEWER_WITH_BOTH =
      """
      {
        "tag_name": "1.2.0",
        "draft": false,
        "prerelease": false,
        "html_url": "https://github.com/rdyjun/mineportal/releases/tag/1.2.0",
        "assets": [
          {
            "name": "MinePortal-win.zip",
            "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.2.0/MinePortal-win.zip"
          },
          {
            "name": "MinePortal-mac-arm64.dmg",
            "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.2.0/MinePortal-mac-arm64.dmg"
          },
          {
            "name": "mineportal-client.jar",
            "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.2.0/mineportal-client.jar"
          }
        ]
      }
      """;
}
