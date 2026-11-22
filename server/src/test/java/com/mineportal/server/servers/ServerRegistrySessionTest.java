package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerRegistrySessionTest {

  private final TrialServerCatalog trialCatalog = mock(TrialServerCatalog.class);
  private ServerRegistry registry;

  @BeforeEach
  void setup() {
    registry = new ServerRegistry(trialCatalog);
  }

  @Test
  void openSessionRemembersTheAddressForThatUserOnly() {
    when(trialCatalog.find("user-a", "local-1")).thenReturn(Optional.empty());

    ServerConfig created =
        registry.openSession("user-a", "local-1", "Play.Example.com.", 25565, "auto");

    assertEquals("play.example.com", created.host);
    assertEquals(25565, created.port);
    assertSame(created, registry.getServer("local-1", "user-a").orElseThrow());
    assertTrue(registry.getServer("local-1", "user-b").isEmpty());
    assertSame(created, registry.openSession("user-a", "local-1", "other.example", 25566, "1.21"));
  }

  @Test
  void anExistingTrialServerIsNotReplacedByTheRequest() {
    ServerConfig trial = new ServerConfig("trial-1", "demo-mc", 25565, "auto");
    when(trialCatalog.find("user-a", "trial-1")).thenReturn(Optional.of(trial));

    ServerConfig opened = registry.openSession("user-a", "trial-1", "evil.example", 25566, "auto");

    assertSame(trial, opened);
    assertEquals("demo-mc", registry.getServer("trial-1", "user-a").orElseThrow().host);
  }

  @Test
  void invalidAddressIsRejected() {
    when(trialCatalog.find("user-a", "local-1")).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> registry.openSession("user-a", "local-1", " ", 25565, "auto"));
  }
}
