package com.mineportal.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppVersionTest {

  @Test
  void parsesBuildTagsAndIgnoresBuildMetadata() {
    assertEquals("1.1.1", version("1.1.1").toString());
    assertEquals("1.1.1", version("v1.1.1").toString());
    assertEquals("1.1.1", version("V1.1.1").toString());
    assertEquals("1.1.1", version("  1.1.1  ").toString());
    assertEquals("1.2.3", version("01.02.03").toString());
    assertEquals("1.2.3", version("1.2.3+build.7").toString());
    assertEquals(version("1.2.3"), version("v1.2.3+build.7"));
    assertTrue(version("1.2.3").stable());
    assertFalse(version("1.2.3-rc.1").stable());
    assertEquals("1.2.3-rc.1", version("1.2.3-rc.01").toString());
  }

  @Test
  void rejectsUnusableVersionStrings() {
    for (String raw :
        new String[] {
          null,
          "",
          " ",
          "v",
          "1",
          "1.2",
          "1.2.3.4",
          "latest",
          "1.2.3-",
          "1.2.3-beta.",
          "1.2.3+",
          "1.2.3+build/evil",
          "MinePortal Client 1.0.0",
          "9999999999.0.0",
          "-1.2.3",
          "1.2.3-béta"
        }) {
      assertTrue(AppVersion.parse(raw).isEmpty(), raw);
    }
  }

  @Test
  void followsSemanticVersionPrecedence() {
    String[] ascending = {
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-alpha.beta",
      "1.0.0-beta",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1",
      "1.0.0",
      "1.1.0",
      "1.1.1",
      "2.0.0"
    };
    for (int i = 0; i < ascending.length - 1; i++) {
      AppVersion lower = version(ascending[i]);
      AppVersion higher = version(ascending[i + 1]);
      assertTrue(lower.compareTo(higher) < 0, ascending[i] + " < " + ascending[i + 1]);
      assertTrue(higher.newerThan(lower));
      assertFalse(lower.newerThan(higher));
    }
    assertTrue(version("1.0.0-1").compareTo(version("1.0.0-alpha")) < 0);
    assertEquals(0, version("1.1.1").compareTo(version("v1.1.1+9")));
  }

  private static AppVersion version(String raw) {
    Optional<AppVersion> parsed = AppVersion.parse(raw);
    assertTrue(parsed.isPresent(), raw);
    return parsed.get();
  }
}
