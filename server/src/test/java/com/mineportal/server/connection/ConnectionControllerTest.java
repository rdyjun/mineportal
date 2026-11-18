package com.mineportal.server.connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mineportal.protocol.MinecraftAddressResolver.ResolvedEndpoint;
import com.mineportal.server.account.AccountSessionManager;
import com.mineportal.server.account.AccountState;
import com.mineportal.server.account.Profile;
import com.mineportal.server.desktop.DesktopConnectionManager;
import com.mineportal.server.servers.ServerConfig;
import com.mineportal.server.servers.ServerRegistry;
import com.mineportal.server.servers.TrialServerAllowlist;
import com.mineportal.server.servers.TrialServerDenial;
import com.mineportal.server.status.PingResult;
import com.mineportal.server.status.PingService;
import com.mineportal.server.ws.EventBroadcaster;
import java.net.InetAddress;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConnectionControllerTest {
  private final ServerRegistry registry = mock(ServerRegistry.class);
  private final EventBroadcaster broadcaster = mock(EventBroadcaster.class);
  private final AccountSessionManager accounts = mock(AccountSessionManager.class);
  private final McConnectionManager relay = mock(McConnectionManager.class);
  private final PingService ping = mock(PingService.class);
  private final DesktopConnectionManager desktop = mock(DesktopConnectionManager.class);
  private final TrialServerAllowlist allowlist = mock(TrialServerAllowlist.class);
  private final ServerConfig server = new ServerConfig("server-a", "example.test", 25565, "auto");
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    AccountState account = new AccountState();
    account.profile = new Profile("owner-a", "TestPlayer");
    when(accounts.get("sid-a")).thenReturn(account);
    when(registry.getServer("server-a", "owner-a")).thenReturn(Optional.of(server));
    when(allowlist.isAllowed(anyString(), anyInt())).thenReturn(true);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ConnectionController(
                    registry, broadcaster, accounts, relay, ping, desktop, allowlist))
            .build();
  }

  @Test
  void desktopRequestRejectsOfflineAppWithoutProbingOrUsingRelay() throws Exception {
    mvc.perform(post("/api/servers/server-a/connect?desktopOnly=true").requestAttr("sid", "sid-a"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("desktop-offline"));

    assertFalse(server.isActive());
    verifyNoInteractions(ping, relay);
    verify(desktop, never()).send(anyString(), anyMap());
  }

  @Test
  void desktopRequestUsesLocalAppAndNeverStartsRelay() throws Exception {
    when(desktop.isConnected("owner-a")).thenReturn(true);
    when(ping.ping(server.host, server.port))
        .thenReturn(PingResult.offline("public probe unavailable"));
    when(desktop.send(eq("owner-a"), anyMap())).thenReturn(true);

    mvc.perform(post("/api/servers/server-a/connect?desktopOnly=true").requestAttr("sid", "sid-a"))
        .andExpect(status().isOk());

    assertTrue(server.isActive());
    verify(desktop).markRouted(server.id, server.currentAttemptId());
    verify(desktop).send(eq("owner-a"), argThat(command -> "connect".equals(command.get("type"))));
    verifyNoInteractions(relay);
  }

  @Test
  void disappearingDesktopFailsInsteadOfFallingBackToRelay() throws Exception {
    when(desktop.isConnected("owner-a")).thenReturn(true);
    when(ping.ping(server.host, server.port)).thenReturn(PingResult.offline("no public route"));
    when(desktop.send(eq("owner-a"), anyMap())).thenReturn(false);

    mvc.perform(post("/api/servers/server-a/connect?desktopOnly=true").requestAttr("sid", "sid-a"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("desktop-offline"));

    assertFalse(server.isActive());
    verifyNoInteractions(relay);
  }

  @Test
  void existingWebConnectionIsNotReportedAsLocalConnection() throws Exception {
    when(desktop.isConnected("owner-a")).thenReturn(true);
    long attempt = server.beginConnection();
    server.markConnected(attempt);

    mvc.perform(post("/api/servers/server-a/connect?desktopOnly=true").requestAttr("sid", "sid-a"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("web-connection-active"));

    verifyNoInteractions(ping, relay);
    assertTrue(server.connected());
  }

  @Test
  void legacyBrowserRequestStillUsesPublicProbe() throws Exception {
    when(ping.observePublic(server.host, server.port))
        .thenThrow(new java.net.UnknownHostException("unresolved test address"));

    mvc.perform(post("/api/servers/server-a/connect").requestAttr("sid", "sid-a"))
        .andExpect(status().isBadRequest());

    verify(ping).observePublic(server.host, server.port);
  }

  @Test
  void relayConnectRejectsServerMissingFromAllowlistBeforeProbing() throws Exception {
    when(allowlist.isAllowed(server.host, server.port)).thenReturn(false);

    mvc.perform(post("/api/servers/server-a/connect").requestAttr("sid", "sid-a"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(TrialServerDenial.CODE))
        .andExpect(jsonPath("$.error").value(TrialServerDenial.MESSAGE));

    assertFalse(server.isActive());
    verifyNoInteractions(ping, relay);
  }

  @Test
  void relayConnectComparesTheUserHostNotTheSrvTarget() throws Exception {
    ResolvedEndpoint endpoint =
        new ResolvedEndpoint(
            "example.test",
            25565,
            "edge.example.test",
            30000,
            InetAddress.getByName("203.0.113.10"),
            true);
    when(ping.observePublic(server.host, server.port))
        .thenReturn(
            new PingService.PingObservation(
                PingResult.online(1, 20, "hello", "1.21", 767, null), endpoint));

    mvc.perform(post("/api/servers/server-a/connect").requestAttr("sid", "sid-a"))
        .andExpect(status().isOk());

    verify(allowlist).isAllowed("example.test", 25565);
    verify(allowlist, never()).isAllowed(eq("edge.example.test"), anyInt());
    verify(relay).connect(eq(server), any(), any(), eq(endpoint), anyLong());
  }

  @Test
  void desktopConnectIgnoresTheTrialAllowlist() throws Exception {
    when(allowlist.isAllowed(anyString(), anyInt())).thenReturn(false);
    when(desktop.isConnected("owner-a")).thenReturn(true);
    when(ping.ping(server.host, server.port)).thenReturn(PingResult.offline("no public route"));
    when(desktop.send(eq("owner-a"), anyMap())).thenReturn(true);
    clearInvocations(allowlist);

    mvc.perform(post("/api/servers/server-a/connect?desktopOnly=true").requestAttr("sid", "sid-a"))
        .andExpect(status().isOk());

    assertTrue(server.isActive());
    verify(allowlist, never()).isAllowed(anyString(), anyInt());
    verify(desktop).send(eq("owner-a"), argThat(command -> "connect".equals(command.get("type"))));
    verifyNoInteractions(relay);
  }
}
