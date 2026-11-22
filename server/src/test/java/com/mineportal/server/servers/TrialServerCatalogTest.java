package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mineportal.server.status.PingResult;
import com.mineportal.server.status.PingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrialServerCatalogTest {

  private final PingService ping = mock(PingService.class);

  @BeforeEach
  void setup() throws Exception {
    when(ping.observePublic(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(new PingService.PingObservation(PingResult.offline("down"), null));
  }

  @Test
  void emptyAllowlistStaysEmpty() {
    TrialServerCatalog catalog = catalog("");

    assertTrue(catalog.list(null).isEmpty());
    assertTrue(catalog.list("user-a").isEmpty());
    assertTrue(catalog.sessionIds("user-a").isEmpty());
  }

  @Test
  void namesComeFromTheMotdAndOnlyFourLowestPortsAreShown() throws Exception {
    TrialServerCatalog catalog =
        catalog("Demo-MC.:25568,demo-mc:25569,demo-mc:25565,demo-mc:25567,demo-mc:25566");
    when(ping.observePublic("demo-mc", 25565))
        .thenReturn(observation("광장 — 채팅 체험 (최대 16명)", 2, 16));
    when(ping.observePublic("demo-mc", 25566)).thenReturn(observation("§a생존§r - 채팅", 0, 16));

    List<TrialServerView> listed = catalog.list(null);

    assertEquals(
        List.of(25565, 25566, 25567, 25568), listed.stream().map(TrialServerView::port).toList());
    assertEquals("광장", listed.get(0).displayName());
    assertEquals("광장 — 채팅 체험 (최대 16명)", listed.get(0).motd());
    assertEquals(2, listed.get(0).playersOnline());
    assertTrue(listed.get(0).online());
    assertEquals("생존", listed.get(1).displayName());
    assertEquals("demo-mc:25567", listed.get(2).displayName());
    assertTrue(listed.get(0).id().startsWith("preview-"));
    assertTrue(catalog.sessionIds("user-a").isEmpty());
  }

  @Test
  void loggedInListReusesOneInMemoryServerPerAddress() {
    TrialServerCatalog catalog = catalog("demo-mc:25565");

    TrialServerView first = catalog.list("user-a").get(0);
    TrialServerView second = catalog.list("user-a").get(0);
    TrialServerView other = catalog.list("user-b").get(0);

    assertEquals(first.id(), second.id());
    assertFalse(first.id().equals(other.id()));
    assertEquals("demo-mc", first.host());
    Optional<ServerConfig> found = catalog.find("user-a", first.id());
    assertTrue(found.isPresent());
    assertEquals(25565, found.get().port);
    assertTrue(catalog.find("user-b", first.id()).isEmpty());
    assertTrue(catalog.find(null, first.id()).isEmpty());
  }

  @Test
  void displayNameFallsBackToTheAddress() {
    assertEquals("demo-mc:25565", TrialServerCatalog.displayName(null, "demo-mc", 25565));
    assertEquals("demo-mc:25565", TrialServerCatalog.displayName("   ", "demo-mc", 25565));
    assertEquals("광장", TrialServerCatalog.displayName("광장 — 채팅 체험", "demo-mc", 25565));
  }

  private TrialServerCatalog catalog(String configured) {
    return new TrialServerCatalog(new TrialServerAllowlist(configured), ping);
  }

  private static PingService.PingObservation observation(String motd, int online, int max) {
    return new PingService.PingObservation(
        PingResult.online(online, max, motd, "demo", 0, null), null);
  }
}
