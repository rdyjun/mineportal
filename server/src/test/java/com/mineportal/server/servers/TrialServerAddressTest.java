package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrialServerAddressTest {

  @Test
  void hostComparisonIgnoresCaseWhitespaceAndATrailingDot() {
    assertEquals("play.example.com", TrialServerAddress.normalizeHost("  Play.Example.COM.  "));
    assertTrue(TrialServerAddress.matches("Play.Example.com.", 25565, "play.example.com", 25565));
    assertTrue(TrialServerAddress.matches("play.example.com", 0, " PLAY.example.com ", 25565));
  }

  @Test
  void omittedUserPortUsesMinecraftDefaultButInvalidStoredPortDoesNotMatch() {
    assertEquals(25565, TrialServerAddress.userPort(0));
    assertEquals(25565, TrialServerAddress.userPort(25565));
    assertEquals(25566, TrialServerAddress.userPort(25566));
    assertFalse(TrialServerAddress.matches("play.example.com", 25565, "play.example.com", 0));
    assertFalse(TrialServerAddress.matches("play.example.com", 25565, "play.example.com", 25566));
  }

  @Test
  void srvTargetIsADifferentHostFromTheNameTheUserTyped() {
    assertFalse(TrialServerAddress.matches("play.example.com", 25565, "edge.example.net", 25565));
    assertFalse(TrialServerAddress.matches("play.example.com", 25565, "edge.example.net", 30000));
  }

  @Test
  void blankHostDoesNotMatch() {
    assertNull(TrialServerAddress.normalizeHost("   "));
    assertNull(TrialServerAddress.normalizeHost(null));
    assertFalse(TrialServerAddress.matches(" ", 25565, "play.example.com", 25565));
    assertFalse(TrialServerAddress.matches("play.example.com", 25565, null, 25565));
  }

  @Test
  void bracketedAddressMatchesTheSameHostWithoutBrackets() {
    assertTrue(TrialServerAddress.matches("[2001:db8::1]", 25565, "2001:db8::1", 25565));
  }
}
