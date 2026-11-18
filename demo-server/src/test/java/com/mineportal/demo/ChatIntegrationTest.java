package com.mineportal.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ChatIntegrationTest {
  @Test
  void twoPlayersChatAndSay() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int port = freePort();
    DemoServers servers = start(sessions, spec("광장", port), limits(16, 256, 5, 10, 600, false, 60));
    try (McTestClient alpha = McTestClient.connect("127.0.0.1", port, "Alpha", sessions);
        McTestClient beta = McTestClient.connect("127.0.0.1", port, "Beta", sessions)) {
      alpha.awaitPlay();
      beta.awaitPlay();
      alpha.chat("hello there");
      alpha.command("say everyone");
      waitUntil(() -> beta.saw("<Alpha> hello there") && beta.saw("[Alpha] everyone"));
      assertTrue(sessions.joins() >= 2);
      assertTrue(sessions.lookups() >= 2);
    } finally {
      servers.close();
    }
  }

  @Test
  void statusReportsNameMaxProtocolAndOnline() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int port = freePort();
    DemoServers servers = start(sessions, spec("광장", port), limits(16, 256, 5, 10, 600, false, 60));
    try (McTestClient alpha = McTestClient.connect("127.0.0.1", port, "Alpha", sessions)) {
      alpha.awaitPlay();
      ServerStatusInfo info = McTestClient.status("127.0.0.1", port);
      String description = McTestClient.plain(info.getDescription());
      assertTrue(description.contains("광장"));
      assertTrue(description.contains("16"));
      assertEquals(16, info.getPlayerInfo().getMaxPlayers());
      assertEquals(1, info.getPlayerInfo().getOnlinePlayers());
      assertEquals(775, info.getVersionInfo().getProtocolVersion());
      assertEquals(
          MinecraftCodec.CODEC.getProtocolVersion(), info.getVersionInfo().getProtocolVersion());
    } finally {
      servers.close();
    }
  }

  @Test
  void instancesAreIsolated() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int plaza = freePort();
    int wild = freePort();
    DemoServers servers =
        DemoServers.start(
            new DemoConfig(
                List.of(spec("광장", plaza), spec("야생", wild)),
                limits(16, 256, 5, 10, 600, false, 60)),
            sessions);
    try (McTestClient left = McTestClient.connect("127.0.0.1", plaza, "Alpha", sessions);
        McTestClient right = McTestClient.connect("127.0.0.1", wild, "Beta", sessions)) {
      left.awaitPlay();
      right.awaitPlay();
      left.chat("only-plaza");
      right.chat("only-wild");
      waitUntil(() -> left.saw("only-plaza") && right.saw("only-wild"));
      assertFalse(left.saw("only-wild"));
      assertFalse(right.saw("only-plaza"));
    } finally {
      servers.close();
    }
  }

  @Test
  void fullInstanceRejectsNextPlayer() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int port = freePort();
    DemoServers servers = start(sessions, spec("광장", port), limits(1, 256, 5, 10, 600, false, 60));
    try (McTestClient alpha = McTestClient.connect("127.0.0.1", port, "Alpha", sessions)) {
      alpha.awaitPlay();
      try (McTestClient beta = McTestClient.connect("127.0.0.1", port, "Beta", sessions)) {
        String reason = beta.awaitDisconnect();
        assertTrue(reason.contains("가득"), reason);
      }
    } finally {
      servers.close();
    }
  }

  @Test
  void lengthAndRateAreNotBroadcast() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int port = freePort();
    DemoServers servers = start(sessions, spec("광장", port), limits(16, 8, 2, 30, 600, false, 60));
    try (McTestClient alpha = McTestClient.connect("127.0.0.1", port, "Alpha", sessions);
        McTestClient beta = McTestClient.connect("127.0.0.1", port, "Beta", sessions)) {
      alpha.awaitPlay();
      beta.awaitPlay();
      alpha.chat("123456789");
      alpha.chat("one");
      alpha.chat("two");
      alpha.chat("three");
      waitUntil(() -> beta.saw("<Alpha> one") && beta.saw("<Alpha> two"));
      Thread.sleep(200);
      assertFalse(beta.saw("123456789"));
      assertFalse(beta.saw("<Alpha> three"));
    } finally {
      servers.close();
    }
  }

  @Test
  void idleDisconnectsWithoutChat() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int port = freePort();
    DemoServers servers = start(sessions, spec("광장", port), limits(16, 256, 5, 10, 1, false, 60));
    try (McTestClient alpha = McTestClient.connect("127.0.0.1", port, "Alpha", sessions)) {
      alpha.awaitPlay();
      String reason = alpha.awaitDisconnect();
      assertTrue(reason.contains("채팅"), reason);
    } finally {
      servers.close();
    }
  }

  @Test
  void botAnnouncesWhileSomeoneIsOnline() throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    int port = freePort();
    DemoServers servers = start(sessions, spec("광장", port), limits(16, 256, 5, 10, 60, true, 1));
    try (McTestClient alpha = McTestClient.connect("127.0.0.1", port, "Alpha", sessions)) {
      alpha.awaitPlay();
      waitUntil(() -> alpha.saw("안내:"));
    } finally {
      servers.close();
    }
  }

  private static DemoServers start(
      RecordingSessionService sessions, DemoServerSpec spec, DemoSettings settings) {
    return DemoServers.start(new DemoConfig(List.of(spec), settings), sessions);
  }

  private static DemoServerSpec spec(String name, int port) {
    return new DemoServerSpec(name, port);
  }

  private static DemoSettings limits(
      int players,
      int chars,
      int limit,
      int windowSeconds,
      int idleSeconds,
      boolean bot,
      int botInterval) {
    return new DemoSettings(
        "127.0.0.1", players, chars, limit, windowSeconds, idleSeconds, bot, botInterval);
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    }
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("timed out waiting for chat");
  }
}
