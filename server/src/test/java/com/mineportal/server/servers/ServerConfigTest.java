package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerConfigTest {

  @Test
  void lifecycleDerivesLegacyPhaseAndConnectedViewFromOneState() {
    ServerConfig server = new ServerConfig("id", "play.example.net", 25565, "current");

    assertEquals("idle", server.phase());
    assertFalse(server.connected());
    long attemptId = server.beginConnection();
    assertTrue(attemptId > 0);
    assertEquals("active", server.phase());
    assertFalse(server.connected());
    assertTrue(server.markConnected(attemptId));
    assertEquals("active", server.phase());
    assertTrue(server.connected());
    assertTrue(server.markClosed());
    assertEquals("closed", server.phase());
    assertFalse(server.connected());
  }

  @Test
  void duplicateStartsAndLateConnectedEventsCannotReviveAClosedAttempt() {
    ServerConfig server = new ServerConfig("id", "play.example.net", 25565, "current");

    long firstAttempt = server.beginConnection();
    assertTrue(firstAttempt > 0);
    assertEquals(0, server.beginConnection());
    assertTrue(server.markFailed(firstAttempt));
    assertFalse(server.markConnected(firstAttempt));
    assertEquals(ConnectionState.FAILED, server.connectionState());
    assertFalse(server.isActive());

    long secondAttempt = server.beginConnection();
    assertTrue(secondAttempt > firstAttempt);
    assertFalse(server.markClosed(firstAttempt));
    assertEquals(ConnectionState.CONNECTING, server.connectionState());
  }
}
