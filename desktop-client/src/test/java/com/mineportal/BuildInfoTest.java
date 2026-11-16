package com.mineportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mineportal.update.AppVersion;
import com.mineportal.update.SafeGithubUrl;
import org.junit.jupiter.api.Test;

class BuildInfoTest {

  @Test
  void versionAndRepoComeFromTheBuild() {
    assertTrue(AppVersion.parse(BuildInfo.VERSION).isPresent());
    assertTrue(AppVersion.parse(BuildInfo.VERSION).orElseThrow().stable());
    assertEquals("rdyjun/mineportal", BuildInfo.GITHUB_REPO);
    assertTrue(SafeGithubUrl.validRepo(BuildInfo.GITHUB_REPO));
  }
}
