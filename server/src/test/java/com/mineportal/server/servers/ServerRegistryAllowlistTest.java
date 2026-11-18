package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mineportal.server.db.ServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerRegistryAllowlistTest {

  private final ServerRepository repository = mock(ServerRepository.class);
  private final TrialServerAllowlist allowlist = mock(TrialServerAllowlist.class);
  private ServerRegistry registry;

  @BeforeEach
  void setup() {
    registry = new ServerRegistry(repository, allowlist);
  }

  @Test
  void trialAddDoesNotInsertWhenTheAllowlistRejects() {
    when(allowlist.isAllowed("play.example.com", 25565)).thenReturn(false);

    assertThrows(
        TrialServerNotAllowedException.class,
        () -> registry.addServer("play.example.com", 25565, "auto", "user-a", true));

    verify(repository, never()).insertServerRow(anyString(), anyString(), anyInt(), anyString());
    verify(repository, never()).linkUserServer(anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void trialAddInsertsWhenTheAddressIsAllowlisted() {
    when(allowlist.isAllowed("play.example.com", 25565)).thenReturn(true);
    when(repository.linkUserServer(eq("user-a"), anyString(), eq("play.example.com"), eq(25565)))
        .thenReturn(true);

    ServerConfig created = registry.addServer("play.example.com", 25565, "auto", "user-a", true);

    assertEquals("play.example.com", created.host);
    assertEquals(25565, created.port);
    verify(repository).insertServerRow(created.id, "play.example.com", 25565, "auto");
  }

  @Test
  void desktopAddSkipsTheAllowlist() {
    when(repository.linkUserServer(eq("user-a"), anyString(), eq("hypixel.net"), eq(25565)))
        .thenReturn(true);

    ServerConfig created = registry.addServer("hypixel.net", 25565, "auto", "user-a", false);

    assertEquals("hypixel.net", created.host);
    verify(allowlist, never()).isAllowed(anyString(), anyInt());
    verify(repository).insertServerRow(created.id, "hypixel.net", 25565, "auto");
  }
}
