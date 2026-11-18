package com.mineportal.demo;

import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.netty.DefaultPacketHandlerExecutor;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

/** One online-mode test client that shares the server's SessionService. */
final class McTestClient implements AutoCloseable {
  static final String TOKEN = "demo-test-token";

  private final ClientNetworkSession session;
  private final List<String> lines = new CopyOnWriteArrayList<>();
  private final CompletableFuture<Void> loggedIn = new CompletableFuture<>();
  private final CompletableFuture<String> disconnected = new CompletableFuture<>();

  private McTestClient(ClientNetworkSession session) {
    this.session = session;
  }

  static McTestClient connect(String host, int port, String name, SessionService sessions) {
    GameProfile profile = new GameProfile(UUID.randomUUID(), name);
    MinecraftProtocol protocol = new MinecraftProtocol(profile, TOKEN);
    Executor executor = DefaultPacketHandlerExecutor.createExecutor();
    ClientNetworkSession session =
        new ClientNetworkSession(new InetSocketAddress(host, port), protocol, executor, null, null);
    session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessions);
    McTestClient client = new McTestClient(session);
    session.addListener(
        new SessionAdapter() {
          @Override
          public void packetReceived(
              org.geysermc.mcprotocollib.network.Session source, Packet packet) {
            if (packet instanceof ClientboundLoginPacket) {
              client.loggedIn.complete(null);
            } else if (packet instanceof ClientboundSystemChatPacket chat) {
              client.lines.add(plain(chat.getContent()));
            }
          }

          @Override
          public void disconnected(DisconnectedEvent event) {
            client.disconnected.complete(plain(event.getReason()));
          }
        });
    session.connect(true);
    return client;
  }

  static ServerStatusInfo status(String host, int port) throws Exception {
    MinecraftProtocol protocol = new MinecraftProtocol();
    Executor executor = DefaultPacketHandlerExecutor.createExecutor();
    ClientNetworkSession session =
        new ClientNetworkSession(new InetSocketAddress(host, port), protocol, executor, null, null);
    CompletableFuture<ServerStatusInfo> info = new CompletableFuture<>();
    session.setFlag(
        MinecraftConstants.SERVER_INFO_HANDLER_KEY, (source, status) -> info.complete(status));
    session.connect(true);
    try {
      return info.get(5, TimeUnit.SECONDS);
    } finally {
      session.disconnect("status done");
    }
  }

  void awaitPlay() throws Exception {
    CompletableFuture.anyOf(loggedIn, disconnected).get(10, TimeUnit.SECONDS);
    if (!loggedIn.isDone()) {
      throw new IllegalStateException(disconnected.getNow("disconnected before login"));
    }
  }

  String awaitDisconnect() throws Exception {
    return disconnected.get(8, TimeUnit.SECONDS);
  }

  void chat(String message) {
    session.send(
        new ServerboundChatPacket(
            message, System.currentTimeMillis(), 0L, null, 0, new BitSet(), 0));
  }

  void command(String command) {
    session.send(new ServerboundChatCommandPacket(command));
  }

  List<String> lines() {
    return List.copyOf(lines);
  }

  boolean saw(String token) {
    for (String line : lines) {
      if (line.contains(token)) {
        return true;
      }
    }
    return false;
  }

  static String plain(Component component) {
    if (component == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    if (component instanceof TextComponent text) {
      builder.append(text.content());
    } else if (component instanceof TranslatableComponent translated) {
      builder.append(translated.key());
      for (TranslationArgument argument : translated.arguments()) {
        Object value = argument.value();
        if (value instanceof Component child) {
          builder.append(plain(child));
        } else if (value != null) {
          builder.append(value);
        }
      }
    }
    for (Component child : component.children()) {
      builder.append(plain(child));
    }
    return builder.toString();
  }

  @Override
  public void close() {
    if (session.isConnected()) {
      session.disconnect("test finished");
    }
  }
}
