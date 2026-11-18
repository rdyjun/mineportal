package com.mineportal.demo;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.status.PlayerInfo;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;
import org.geysermc.mcprotocollib.protocol.data.status.VersionInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One online-mode chat world. Movement and blocks are not implemented. */
public final class DemoInstance {
  private static final Logger LOGGER = LoggerFactory.getLogger(DemoInstance.class);
  private static final Key OVERWORLD = Key.key("minecraft:overworld");
  private static final int STATUS_SAMPLE = 12;

  private final DemoServerSpec spec;
  private final DemoSettings settings;
  private final DemoNetworkServer server;
  private final ScheduledExecutorService scheduler;
  private final AtomicInteger nextEntityId = new AtomicInteger(1);
  private final Object lock = new Object();
  private final Map<Session, Player> players = new LinkedHashMap<>();
  private ScheduledFuture<?> idleTask;
  private ScheduledFuture<?> botTask;
  private boolean closed;

  private DemoInstance(
      DemoServerSpec spec,
      DemoSettings settings,
      DemoNetworkServer server,
      ScheduledExecutorService scheduler) {
    this.spec = spec;
    this.settings = settings;
    this.server = server;
    this.scheduler = scheduler;
  }

  static DemoInstance start(
      DemoServerSpec spec,
      DemoSettings settings,
      SessionService sessionService,
      ScheduledExecutorService scheduler) {
    DemoNetworkServer server =
        new DemoNetworkServer(new InetSocketAddress(settings.bindHost(), spec.port()));
    server.setGlobalFlag(MinecraftConstants.ENCRYPT_CONNECTION, true);
    server.setGlobalFlag(MinecraftConstants.SHOULD_AUTHENTICATE, true);
    server.setGlobalFlag(MinecraftConstants.ACCEPT_TRANSFERS_KEY, false);
    server.setGlobalFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
    server.setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 256);
    DemoInstance instance = new DemoInstance(spec, settings, server, scheduler);
    server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, instance::status);
    server.setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, instance::loggedIn);
    try {
      server.bind(true);
    } catch (RuntimeException failure) {
      server.close(false);
      throw failure;
    }
    if (!server.isListening()) {
      server.close(false);
      throw new IllegalStateException(
          "demo server " + spec.name() + " is not listening on port " + spec.port());
    }
    instance.schedule();
    LOGGER.info("Listening for {} on {}:{}", spec.name(), settings.bindHost(), spec.port());
    return instance;
  }

  private void schedule() {
    long periodMillis = Math.max(200L, Math.min(5000L, settings.idleTimeoutSeconds() * 1000L / 2L));
    idleTask =
        scheduler.scheduleAtFixedRate(
            this::disconnectIdle, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    if (settings.botEnabled()) {
      long interval = settings.botIntervalSeconds();
      botTask = scheduler.scheduleAtFixedRate(this::botLine, interval, interval, TimeUnit.SECONDS);
    }
  }

  void close() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
    }
    if (idleTask != null) {
      idleTask.cancel(false);
    }
    if (botTask != null) {
      botTask.cancel(false);
    }
    server.close(true);
  }

  private ServerStatusInfo status(Session session) {
    int online;
    List<GameProfile> sample = new ArrayList<>();
    synchronized (lock) {
      online = players.size();
      for (Player player : players.values()) {
        if (sample.size() >= STATUS_SAMPLE) {
          break;
        }
        sample.add(player.profile);
      }
    }
    String description = spec.name() + " — 채팅 체험 (최대 " + settings.maxPlayers() + "명)";
    return new ServerStatusInfo(
        Component.text(description),
        new PlayerInfo(settings.maxPlayers(), online, sample),
        new VersionInfo(
            MinecraftCodec.CODEC.getMinecraftVersion(), MinecraftCodec.CODEC.getProtocolVersion()),
        null,
        false);
  }

  private void loggedIn(Session session) {
    GameProfile profile = session.getFlag(MinecraftConstants.PROFILE_KEY);
    if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
      session.disconnect(Component.text("프로필을 확인할 수 없어요."));
      return;
    }
    String rejection = null;
    synchronized (lock) {
      if (closed) {
        rejection = "서버가 닫히는 중이에요.";
      } else if (players.size() >= settings.maxPlayers()) {
        rejection = "이 서버는 가득 찼어요.";
      } else if (nameTaken(profile.getName())) {
        rejection = "이미 같은 이름의 플레이어가 있어요.";
      } else {
        Player joining =
            new Player(
                session,
                profile,
                displayName(profile.getName()),
                new ChatGuard(settings),
                nextEntityId.getAndIncrement());
        joining.guard.touch();
        players.put(session, joining);
      }
    }
    if (rejection != null) {
      session.disconnect(Component.text(rejection));
      return;
    }
    Player player = find(session);
    if (player == null) {
      return;
    }
    session.addListener(
        new SessionAdapter() {
          @Override
          public void packetReceived(Session source, Packet packet) {
            onPacket(source, packet);
          }

          @Override
          public void disconnected(DisconnectedEvent event) {
            onDisconnected(session);
          }
        });
    session.send(loginPacket(player));
    session.send(
        new ClientboundSetDefaultSpawnPositionPacket(
            new GlobalPos(OVERWORLD, Vector3i.from(0, 65, 0)), 0.0f, 0.0f));
    session.send(new ClientboundPlayerPositionPacket(1, 0.0, 65.0, 0.0, 0.0, 0.0, 0.0, 0.0f, 0.0f));
    tell(session, spec.name() + "에 들어왔어요. 여기서는 채팅만 할 수 있어요.");
    broadcast(player.displayName + "님이 들어왔어요.");
  }

  private boolean nameTaken(String name) {
    for (Player player : players.values()) {
      if (player.profile.getName().equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private void onPacket(Session session, Packet packet) {
    if (packet instanceof ServerboundChatPacket chat) {
      handleLine(session, chat.getMessage());
    } else if (packet instanceof ServerboundChatCommandPacket command) {
      handleLine(session, "/" + command.getCommand());
    } else if (packet instanceof ServerboundChatCommandSignedPacket command) {
      handleLine(session, "/" + command.getCommand());
    }
  }

  private void handleLine(Session session, String raw) {
    Player player = find(session);
    if (player == null) {
      return;
    }
    String text = stripControls(raw).trim();
    switch (player.guard.admit(text)) {
      case ACCEPT -> deliver(player, text);
      case TOO_LONG ->
          tell(session, "메시지가 너무 길어요. 최대 " + settings.maxMessageChars() + "자까지 보낼 수 있어요.");
      case TOO_FAST -> tell(session, "메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내 주세요.");
      case BLANK, SILENT -> {
        // Nothing is sent. A repeated length or rate failure stays silent.
      }
      default -> {
        // Decision is exhaustive.
      }
    }
  }

  private void deliver(Player player, String text) {
    if (text.startsWith("/")) {
      DemoCommands.Reply reply =
          DemoCommands.handle(player.displayName, onlineNames(), text.substring(1));
      if (reply.toEveryone() != null) {
        broadcast(reply.toEveryone());
      }
      if (reply.toSender() != null) {
        tell(player.session, reply.toSender());
      }
      return;
    }
    broadcast("<" + player.displayName + "> " + text);
  }

  private void onDisconnected(Session session) {
    Player removed;
    boolean announce;
    synchronized (lock) {
      removed = players.remove(session);
      announce = removed != null && !closed;
    }
    if (announce) {
      broadcast(removed.displayName + "님이 나갔어요.");
    }
  }

  private void disconnectIdle() {
    if (isClosed()) {
      return;
    }
    List<Session> idle = new ArrayList<>();
    synchronized (lock) {
      for (Player player : players.values()) {
        if (player.guard.idle()) {
          idle.add(player.session);
        }
      }
    }
    for (Session session : idle) {
      session.disconnect(Component.text("한동안 채팅이 없어 연결을 닫았어요."));
    }
  }

  private void botLine() {
    if (isClosed()) {
      return;
    }
    boolean anyone;
    synchronized (lock) {
      anyone = !players.isEmpty();
    }
    if (anyone) {
      broadcast("안내: 여기는 채팅만 할 수 있는 체험 공간이에요.");
    }
  }

  private ClientboundLoginPacket loginPacket(Player player) {
    return new ClientboundLoginPacket(
        player.entityId,
        false,
        new Key[] {OVERWORLD},
        settings.maxPlayers(),
        2,
        2,
        true,
        false,
        true,
        new PlayerSpawnInfo(
            0, OVERWORLD, 0L, GameMode.ADVENTURE, GameMode.ADVENTURE, false, true, null, 0, 63),
        false);
  }

  private List<String> onlineNames() {
    synchronized (lock) {
      List<String> names = new ArrayList<>();
      for (Player player : players.values()) {
        names.add(player.displayName);
      }
      return names;
    }
  }

  private Player find(Session session) {
    synchronized (lock) {
      return players.get(session);
    }
  }

  private boolean isClosed() {
    synchronized (lock) {
      return closed;
    }
  }

  private void tell(Session session, String line) {
    send(session, line);
  }

  private void broadcast(String line) {
    List<Session> targets = new ArrayList<>();
    synchronized (lock) {
      for (Player player : players.values()) {
        targets.add(player.session);
      }
    }
    for (Session target : targets) {
      send(target, line);
    }
  }

  private static void send(Session session, String line) {
    try {
      session.send(new ClientboundSystemChatPacket(Component.text(line), false));
    } catch (RuntimeException ignored) {
      // The session can close between the snapshot and the write.
    }
  }

  static String stripControls(String raw) {
    if (raw == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder(raw.length());
    raw.codePoints()
        .forEach(
            codePoint -> {
              if (Character.isISOControl(codePoint)) {
                builder.append(' ');
              } else {
                builder.appendCodePoint(codePoint);
              }
            });
    return builder.toString();
  }

  static String displayName(String name) {
    if (name.codePointCount(0, name.length()) <= 16) {
      return name;
    }
    return name.substring(0, name.offsetByCodePoints(0, 16));
  }

  private static final class Player {
    private final Session session;
    private final GameProfile profile;
    private final String displayName;
    private final ChatGuard guard;
    private final int entityId;

    private Player(
        Session session, GameProfile profile, String displayName, ChatGuard guard, int entityId) {
      this.session = session;
      this.profile = profile;
      this.displayName = displayName;
      this.guard = guard;
      this.entityId = entityId;
    }
  }
}
