package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mineportal.server.account.AccountSessionManager;
import com.mineportal.server.account.AccountState;
import com.mineportal.server.account.Profile;
import com.mineportal.server.desktop.DesktopConnectionManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ServersControllerAllowlistTest {

  private final ServerRegistry registry = mock(ServerRegistry.class);
  private final AccountSessionManager accounts = mock(AccountSessionManager.class);
  private final DesktopConnectionManager desktop = mock(DesktopConnectionManager.class);
  private ServersController controller;

  @BeforeEach
  void setup() {
    AccountState account = new AccountState();
    account.profile = new Profile("owner-a", "TestPlayer");
    when(accounts.get("sid-a")).thenReturn(account);
    controller = new ServersController(registry, accounts, desktop);
  }

  @Test
  void trialAddAsksTheRegistryToEnforceTheAllowlist() {
    when(desktop.isConnected("owner-a")).thenReturn(false);
    when(registry.addServer("Play.Example.com", 25565, "auto", "owner-a", true))
        .thenThrow(new TrialServerNotAllowedException());

    ResponseEntity<?> response =
        controller.add("sid-a", new AddServerRequest("Play.Example.com", "auto"));

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertInstanceOf(Map.class, response.getBody());
    Map<?, ?> body = (Map<?, ?>) response.getBody();
    assertEquals(TrialServerDenial.CODE, body.get("code"));
    assertEquals(TrialServerDenial.MESSAGE, body.get("error"));
  }

  @Test
  void explicitPortIsPassedThroughToTheAllowlistCheck() {
    when(desktop.isConnected("owner-a")).thenReturn(false);
    when(registry.addServer("play.example.com", 25566, "auto", "owner-a", true))
        .thenReturn(new ServerConfig("id-1", "play.example.com", 25566, "auto"));

    ResponseEntity<?> response =
        controller.add("sid-a", new AddServerRequest("play.example.com:25566", "auto"));

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    verify(registry).addServer("play.example.com", 25566, "auto", "owner-a", true);
  }

  @Test
  void desktopAddDoesNotEnforceTheAllowlist() {
    when(desktop.isConnected("owner-a")).thenReturn(true);
    when(registry.addServer("hypixel.net", 25565, "auto", "owner-a", false))
        .thenReturn(new ServerConfig("id-2", "hypixel.net", 25565, "auto"));

    ResponseEntity<?> response =
        controller.add("sid-a", new AddServerRequest("hypixel.net", "auto"));

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    verify(registry).addServer("hypixel.net", 25565, "auto", "owner-a", false);
    verify(registry, never()).addServer("hypixel.net", 25565, "auto", "owner-a", true);
  }
}
