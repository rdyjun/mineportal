package com.mineportal.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoConfigTest {
  @Test
  void defaultServerIsTrialOn25565() {
    DemoConfig config = DemoConfig.fromEnvironment(Map.of());

    assertEquals(1, config.servers().size());
    assertEquals("체험", config.servers().get(0).name());
    assertEquals(25565, config.servers().get(0).port());
    assertEquals("0.0.0.0", config.settings().bindHost());
    assertEquals(16, config.settings().maxPlayers());
    assertEquals(256, config.settings().maxMessageChars());
    assertEquals(5, config.settings().messageLimit());
    assertEquals(10, config.settings().messageWindowSeconds());
    assertEquals(600, config.settings().idleTimeoutSeconds());
    assertFalse(config.settings().botEnabled());
    assertEquals(60, config.settings().botIntervalSeconds());
  }

  @Test
  void fourServersAreAccepted() {
    DemoConfig config =
        DemoConfig.fromEnvironment(Map.of("DEMO_SERVERS", "광장:25565,생존:25566,야생:25567,미니게임:25568"));

    assertEquals(4, config.servers().size());
    assertEquals("광장", config.servers().get(0).name());
    assertEquals(25568, config.servers().get(3).port());
  }

  @Test
  void fiveServersAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DemoConfig.fromEnvironment(Map.of("DEMO_SERVERS", "a:1,b:2,c:3,d:4,e:5")));
  }

  @Test
  void blankServerListIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DemoConfig.fromEnvironment(Map.of("DEMO_SERVERS", "   ")));
  }

  @Test
  void duplicatePortIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DemoConfig.fromEnvironment(Map.of("DEMO_SERVERS", "광장:25565,생존:25565")));
  }

  @Test
  void parsesBoolAndIntegerValues() {
    DemoConfig enabled =
        DemoConfig.fromEnvironment(
            Map.of(
                "DEMO_BOT_ENABLED",
                "YES",
                "DEMO_MAX_PLAYERS",
                "3",
                "DEMO_MAX_MESSAGE_CHARS",
                "8",
                "DEMO_MESSAGE_LIMIT",
                "2",
                "DEMO_MESSAGE_WINDOW_SECONDS",
                "30",
                "DEMO_IDLE_TIMEOUT_SECONDS",
                "1",
                "DEMO_BOT_INTERVAL_SECONDS",
                "1"));
    assertTrue(enabled.settings().botEnabled());
    assertEquals(3, enabled.settings().maxPlayers());
    assertEquals(8, enabled.settings().maxMessageChars());
    assertEquals(2, enabled.settings().messageLimit());
    assertEquals(30, enabled.settings().messageWindowSeconds());
    assertEquals(1, enabled.settings().idleTimeoutSeconds());
    assertEquals(1, enabled.settings().botIntervalSeconds());

    DemoConfig disabled =
        DemoConfig.fromEnvironment(
            Map.of("DEMO_BOT_ENABLED", "off", "DEMO_BIND_HOST", "127.0.0.1"));
    assertFalse(disabled.settings().botEnabled());
    assertEquals("127.0.0.1", disabled.settings().bindHost());

    assertThrows(
        IllegalArgumentException.class,
        () -> DemoConfig.fromEnvironment(Map.of("DEMO_BOT_ENABLED", "maybe")));
    assertThrows(
        IllegalArgumentException.class,
        () -> DemoConfig.fromEnvironment(Map.of("DEMO_MAX_PLAYERS", "0")));
    assertThrows(
        IllegalArgumentException.class,
        () -> DemoConfig.fromEnvironment(Map.of("DEMO_MESSAGE_LIMIT", "nope")));
  }
}
