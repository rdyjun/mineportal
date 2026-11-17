package com.mineportal.server.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mineportal.server.db.AllowedServerAddress;
import com.mineportal.server.db.ServerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrialServerAllowlistTest {

  private final ServerRepository repository = mock(ServerRepository.class);
  private final TrialServerAllowlist allowlist = new TrialServerAllowlist(repository);

  @Test
  void emptyTableDeniesEveryAddress() {
    when(repository.listTrialAllowedServers()).thenReturn(List.of());

    assertFalse(allowlist.isAllowed("play.example.com", 25565));
    assertFalse(allowlist.isAllowed("play.example.com", 0));
  }

  @Test
  void lookupFailureDeniesEveryAddress() {
    when(repository.listTrialAllowedServers()).thenThrow(new IllegalStateException("db down"));

    assertFalse(allowlist.isAllowed("play.example.com", 25565));
  }

  @Test
  void listedHostMatchesIgnoringCaseAndDefaultPortButNotAnSrvTarget() {
    when(repository.listTrialAllowedServers())
        .thenReturn(
            List.of(
                new AllowedServerAddress("Play.Example.com.", 25565),
                new AllowedServerAddress("other.test", 25566)));

    assertTrue(allowlist.isAllowed("play.example.com", 25565));
    assertTrue(allowlist.isAllowed("PLAY.EXAMPLE.COM", 0));
    assertFalse(allowlist.isAllowed("edge.example.net", 25565));
    assertFalse(allowlist.isAllowed("other.test", 25565));
    assertTrue(allowlist.isAllowed("other.test", 25566));
  }
}
