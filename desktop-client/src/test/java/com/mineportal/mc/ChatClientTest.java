package com.mineportal.mc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mineportal.auth.Account;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundTransferPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatClientTest {
  private final Account account = new Account(UUID.randomUUID(), "TestPlayer", "unused", null);
  private final ChatClient.Listener events = mock(ChatClient.Listener.class);
  private final ClientSession first = mock(ClientSession.class);
  private final ClientSession second = mock(ClientSession.class);

  private SessionListener handler(ClientSession session) {
    ArgumentCaptor<SessionListener> captor = ArgumentCaptor.forClass(SessionListener.class);
    verify(session).addListener(captor.capture());
    return captor.getValue();
  }

  @Test
  void tcpStartupIsNotReportedAsMinecraftLogin() {
    ChatClient client = new ChatClient(account, events, (h, p, v, f, t) -> first);
    client.connect("first.test", 25565, 0, -1);
    verifyNoInteractions(events);

    handler(first).packetReceived(first, mock(ClientboundLoginPacket.class));
    handler(first).packetReceived(first, mock(ClientboundLoginPacket.class));
    verify(events, times(1)).onConnected();
  }

  @Test
  void transferOwnsReplacementAndIgnoresOldDisconnect() throws Exception {
    ChatClient.SessionFactory factory = mock(ChatClient.SessionFactory.class);
    when(factory.create("first.test", 25565, 0, -1, false)).thenReturn(first);
    when(factory.create("second.test", 25566, 0, -1, true)).thenReturn(second);
    ChatClient client = new ChatClient(account, events, factory);
    client.connect("first.test", 25565, 0, -1);
    SessionListener original = handler(first);

    original.packetReceived(first, new ClientboundTransferPacket("second.test", 25566));
    verify(second, timeout(2000)).connect(false);
    original.disconnected(new DisconnectedEvent(first, Component.text("transfer"), null));
    verifyNoInteractions(events);
    verify(second).setFlag(BuiltinFlags.CLIENT_TRANSFERRING, true);
    verify(second).setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);

    client.disconnect();
    verify(second).disconnect(any(Component.class));
    verify(first).disconnect(any(Component.class));
  }

  @Test
  void cancellationWhileResolvingTransferCannotStartReplacement() throws Exception {
    CountDownLatch resolving = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch replacementClosed = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              replacementClosed.countDown();
              return null;
            })
        .when(second)
        .disconnect(any(Component.class));
    ChatClient client =
        new ChatClient(
            account,
            events,
            (h, p, v, f, transferring) -> {
              if (!transferring) return first;
              resolving.countDown();
              try {
                if (!release.await(2, TimeUnit.SECONDS))
                  throw new IllegalStateException("test release timeout");
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
              }
              return second;
            });
    client.connect("first.test", 25565, 0, -1);
    handler(first).packetReceived(first, new ClientboundTransferPacket("second.test", 25565));
    try {
      assertTrue(resolving.await(2, TimeUnit.SECONDS));
      client.disconnect();
    } finally {
      release.countDown();
    }
    assertTrue(replacementClosed.await(2, TimeUnit.SECONDS));
    verify(second, never()).connect(anyBoolean());
    verifyNoInteractions(events);
  }

  @Test
  void staleLoginPacketAfterUserDisconnectCannotReviveConnection() {
    ChatClient client = new ChatClient(account, events, (h, p, v, f, t) -> first);
    client.connect("first.test", 25565, 0, -1);
    SessionListener original = handler(first);
    client.disconnect();
    original.packetReceived(first, mock(ClientboundLoginPacket.class));
    original.disconnected(new DisconnectedEvent(first, Component.text("closed"), null));
    verifyNoInteractions(events);
  }

  @Test
  void disconnectReasonIncludesTranslatedArguments() {
    ChatClient client = new ChatClient(account, events, (h, p, v, f, t) -> first);
    client.connect("first.test", 25565, 0, -1);
    handler(first)
        .disconnected(
            new DisconnectedEvent(
                first,
                Component.translatable(
                    "disconnect.genericReason", Component.text("Connection refused")),
                null));
    verify(events).onDisconnected("Connection refused");
  }

  @Test
  void transferResolutionFailureIsReportedOnce() {
    ChatClient client =
        new ChatClient(
            account,
            events,
            (h, p, v, f, transferring) -> {
              if (transferring) throw new IllegalArgumentException("unresolvable target");
              return first;
            });
    client.connect("first.test", 25565, 0, -1);
    SessionListener original = handler(first);
    original.packetReceived(first, new ClientboundTransferPacket("second.test", 25565));
    verify(events, timeout(2000)).onDisconnected("Server transfer failed: unresolvable target");
    original.disconnected(new DisconnectedEvent(first, Component.text("old session"), null));
    verify(events, times(1)).onDisconnected(anyString());
  }
}
