package com.mineportal.server.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mineportal.protocol.MinecraftAddressResolver.ResolvedEndpoint;
import com.mineportal.server.servers.ConnectionState;
import com.mineportal.server.servers.ServerConfig;
import com.mineportal.server.servers.TrialServerAllowlist;
import com.mineportal.server.servers.TrialServerDenial;
import com.mineportal.server.ws.EventBroadcaster;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class McConnectionManagerAllowlistTest {

  @Test
  void relayDoesNotOpenASessionWhenTheUserHostIsNotAllowlisted() throws Exception {
    EventBroadcaster broadcaster = mock(EventBroadcaster.class);
    TrialServerAllowlist allowlist = mock(TrialServerAllowlist.class);
    McConnectionManager manager = new McConnectionManager(broadcaster, allowlist);
    ServerConfig server = new ServerConfig("server-a", "Play.Example.com", 25565, "auto");
    long attemptId = server.beginConnection();
    when(allowlist.isAllowed("Play.Example.com", 25565)).thenReturn(false);
    clearInvocations(allowlist);
    ResolvedEndpoint endpoint =
        new ResolvedEndpoint(
            "play.example.com",
            25565,
            "edge.example.net",
            30000,
            InetAddress.getByName("203.0.113.10"),
            true);

    manager.connect(server, null, null, endpoint, attemptId);

    assertEquals(ConnectionState.FAILED, server.connectionState());
    verify(allowlist).isAllowed("Play.Example.com", 25565);
    verify(allowlist, never()).isAllowed(eq("edge.example.net"), anyInt());
    verify(allowlist, never()).isAllowed(anyString(), eq(30000));
    verify(broadcaster).setStatus(server, "connection failed: " + TrialServerDenial.MESSAGE, true);
  }
}
