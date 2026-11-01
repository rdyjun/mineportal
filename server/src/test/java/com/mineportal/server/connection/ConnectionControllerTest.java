package com.mineportal.server.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mineportal.server.account.AccountSessionManager;
import com.mineportal.server.account.AccountState;
import com.mineportal.server.account.Profile;
import com.mineportal.server.desktop.DesktopConnectionManager;
import com.mineportal.server.servers.ServerConfig;
import com.mineportal.server.servers.ServerRegistry;
import com.mineportal.server.status.PingResult;
import com.mineportal.server.status.PingService;
import com.mineportal.server.ws.EventBroadcaster;
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
  private final ServerConfig server = new ServerConfig("server-a", "example.test", 25565, "auto");
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    AccountState account = new AccountState();
    account.profile = new Profile("owner-a", "TestPlayer");
    when(accounts.get("sid-a")).thenReturn(account);
    when(registry.getServer("server-a", "owner-a")).thenReturn(Optional.of(server));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ConnectionController(registry, broadcaster, accounts, relay, ping, desktop))
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
}
