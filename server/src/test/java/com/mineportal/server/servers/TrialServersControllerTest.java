package com.mineportal.server.servers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mineportal.server.account.AccountSessionManager;
import com.mineportal.server.account.AccountState;
import com.mineportal.server.desktop.DesktopConnectionManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrialServersControllerTest {

  private final ServerRegistry registry = mock(ServerRegistry.class);
  private final AccountSessionManager accounts = mock(AccountSessionManager.class);
  private final DesktopConnectionManager desktop = mock(DesktopConnectionManager.class);
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    when(accounts.get("sid-x")).thenReturn(new AccountState());
    mvc =
        MockMvcBuilders.standaloneSetup(new ServersController(registry, accounts, desktop)).build();
  }

  @Test
  void trialRouteReturnsTheCatalogForTheSessionOwner() throws Exception {
    when(registry.listTrialServers(null))
        .thenReturn(
            List.of(
                new TrialServerView(
                    "preview-demo-mc-25565",
                    "demo-mc",
                    25565,
                    "auto",
                    "idle",
                    "idle",
                    false,
                    "광장",
                    "광장 — 채팅 체험",
                    true,
                    1,
                    16)));

    mvc.perform(get("/api/servers/trial").requestAttr("sid", "sid-x"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].displayName").value("광장"))
        .andExpect(jsonPath("$[0].host").value("demo-mc"))
        .andExpect(jsonPath("$[0].port").value(25565))
        .andExpect(jsonPath("$[0].online").value(true));
  }
}
