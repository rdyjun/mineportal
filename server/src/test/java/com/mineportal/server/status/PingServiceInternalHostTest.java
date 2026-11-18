package com.mineportal.server.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.UnknownHostException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PingServiceInternalHostTest {
  @Test
  void emptySetStillRejectsPrivateAddresses() {
    PingService ping = new PingService(Set.of());
    assertThrows(UnknownHostException.class, () -> ping.observePublic("127.0.0.1", 1));
    assertThrows(UnknownHostException.class, () -> ping.observePublic("10.1.2.3", 1));
    assertThrows(UnknownHostException.class, () -> ping.observePublic("172.17.0.2", 1));
  }

  @Test
  void namedInternalHostMayResolveToLoopback() throws Exception {
    PingService ping = new PingService(Set.of("localhost"));
    PingService.PingObservation observation = ping.observePublic("LocalHost.", 1);
    assertFalse(observation.result().online());
    assertThrows(UnknownHostException.class, () -> ping.observePublic("127.0.0.1", 1));
    assertThrows(UnknownHostException.class, () -> ping.observePublic("10.1.2.3", 1));
  }

  @Test
  void parseAcceptsHostnameAndRejectsIpLiterals() {
    assertEquals(Set.of("demo-mc"), PingService.parseRelayInternalHosts(" Demo-MC. "));
    assertThrows(
        IllegalStateException.class, () -> PingService.parseRelayInternalHosts("10.0.0.5"));
    assertThrows(IllegalStateException.class, () -> PingService.parseRelayInternalHosts("::1"));
  }
}
