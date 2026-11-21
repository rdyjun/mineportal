package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrialServerAllowlistTest {

  @Test
  void blankConfigurationDeniesEveryAddress() {
    TrialServerAllowlist allowlist = new TrialServerAllowlist("");

    assertFalse(allowlist.isAllowed("play.example.com", 25565));
    assertFalse(allowlist.isAllowed("play.example.com", 0));
    assertTrue(allowlist.entries().isEmpty());
  }

  @Test
  void defaultConfigurationIsTheFourDemoServers() {
    TrialServerAllowlist allowlist = new TrialServerAllowlist(TrialServerAllowlist.DEFAULT_SERVERS);

    assertEquals(4, allowlist.entries().size());
    assertTrue(allowlist.isAllowed("demo-mc", 25565));
    assertTrue(allowlist.isAllowed("DEMO-MC", 25566));
    assertTrue(allowlist.isAllowed("demo-mc", 25567));
    assertTrue(allowlist.isAllowed("demo-mc", 25568));
    assertFalse(allowlist.isAllowed("demo-mc", 25569));
    assertFalse(allowlist.isAllowed("play.example.com", 25565));
  }

  @Test
  void listedHostMatchesIgnoringCaseAndDefaultPort() {
    TrialServerAllowlist allowlist =
        new TrialServerAllowlist("Play.Example.com.:25565, other.test:25566");

    assertTrue(allowlist.isAllowed("play.example.com", 25565));
    assertTrue(allowlist.isAllowed("PLAY.EXAMPLE.COM", 0));
    assertFalse(allowlist.isAllowed("edge.example.net", 25565));
    assertFalse(allowlist.isAllowed("other.test", 25565));
    assertTrue(allowlist.isAllowed("other.test", 25566));
  }
}
