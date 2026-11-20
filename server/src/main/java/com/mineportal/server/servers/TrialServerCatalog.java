package com.mineportal.server.servers;

import com.mineportal.server.db.AllowedServerAddress;
import com.mineportal.server.db.ServerRepository;
import com.mineportal.server.status.PingResult;
import com.mineportal.server.status.PingService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 웹/앱 체험 모드가 고를 수 있는 서버. 출처는 trial_allowed_servers이고, 사용자별 servers/user_servers 행은 만들지 않는다. 데스크톱 정식
 * 모드의 저장 목록과 섞이지 않게, 접속 상태만 계정별로 메모리에 둔다.
 */
@Component
public class TrialServerCatalog {

  static final int MAX_SERVERS = 4;

  private final ServerRepository repository;
  private final PingService pingService;
  private final Map<String, Map<String, ServerConfig>> sessions = new ConcurrentHashMap<>();

  public TrialServerCatalog(ServerRepository repository, PingService pingService) {
    this.repository = repository;
    this.pingService = pingService;
  }

  /** userId가 null이면 로그인 전 미리보기라 접속 상태를 만들지 않는다. */
  public List<TrialServerView> list(String userId) {
    List<AllowedAddress> allowed = allowedAddresses();
    if (userId == null) {
      return allowed.stream().map(row -> preview(row, probe(row))).toList();
    }
    Map<String, ServerConfig> mine =
        sessions.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
    List<TrialServerView> views = new ArrayList<>();
    synchronized (mine) {
      for (AllowedAddress row : allowed) {
        String id = trialId(userId, row.host(), row.port());
        ServerConfig config =
            mine.computeIfAbsent(id, key -> new ServerConfig(key, row.host(), row.port(), "auto"));
        views.add(view(config, probe(row)));
      }
    }
    return views;
  }

  public Optional<ServerConfig> find(String userId, String id) {
    if (userId == null || id == null) {
      return Optional.empty();
    }
    Map<String, ServerConfig> mine = sessions.get(userId);
    if (mine == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mine.get(id));
  }

  /** 로그아웃·세션 종료 때 끊어야 하는, 이 계정의 체험 접속 전체. */
  public List<ServerConfig> live(String userId) {
    Map<String, ServerConfig> mine = sessions.get(userId);
    if (mine == null) {
      return List.of();
    }
    return List.copyOf(mine.values());
  }

  public void forget(String userId) {
    if (userId != null) {
      sessions.remove(userId);
    }
  }

  static String trialId(String userId, String host, int port) {
    String key = userId + "\n" + host + "\n" + port;
    return "trial-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }

  /** MOTD의 첫 줄에서 이름만 고른다. 데모 서버는 "광장 — 채팅 체험 …" 형태다. 없으면 주소. */
  static String displayName(String motd, String host, int port) {
    String plain = plainMotd(motd);
    if (plain == null) {
      return host + ":" + port;
    }
    String line = plain.split("\\R", 2)[0].trim();
    int split = separator(line);
    String name = split > 0 ? line.substring(0, split).trim() : line;
    if (name.isEmpty()) {
      name = line;
    }
    if (name.codePointCount(0, name.length()) > 32) {
      name = name.substring(0, name.offsetByCodePoints(0, 32)).trim();
    }
    return name.isEmpty() ? host + ":" + port : name;
  }

  private List<AllowedAddress> allowedAddresses() {
    List<AllowedServerAddress> rows;
    try {
      rows = repository.listTrialAllowedServers();
    } catch (RuntimeException failure) {
      System.err.println("trial catalog lookup failed: " + failure.getMessage());
      return List.of();
    }
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<AllowedAddress> allowed = new ArrayList<>();
    for (AllowedServerAddress row : rows) {
      if (row == null) {
        continue;
      }
      String host = TrialServerAddress.normalizeHost(row.host());
      if (host == null || row.port() < 1 || row.port() > 65535) {
        continue;
      }
      allowed.add(new AllowedAddress(host, row.port()));
    }
    allowed.sort(Comparator.comparingInt(AllowedAddress::port).thenComparing(AllowedAddress::host));
    if (allowed.size() > MAX_SERVERS) {
      return List.copyOf(allowed.subList(0, MAX_SERVERS));
    }
    return List.copyOf(allowed);
  }

  private PingResult probe(AllowedAddress row) {
    try {
      return pingService.observePublic(row.host(), row.port()).result();
    } catch (Exception failure) {
      return PingResult.offline("unavailable");
    }
  }

  private static TrialServerView preview(AllowedAddress row, PingResult ping) {
    return new TrialServerView(
        "preview-" + row.host() + "-" + row.port(),
        row.host(),
        row.port(),
        "auto",
        "idle",
        "idle",
        false,
        displayName(ping.motd(), row.host(), row.port()),
        plainMotd(ping.motd()),
        ping.online(),
        ping.playersOnline(),
        ping.playersMax());
  }

  private static TrialServerView view(ServerConfig config, PingResult ping) {
    return new TrialServerView(
        config.id,
        config.host,
        config.port,
        config.version,
        config.status,
        config.phase(),
        config.connected(),
        displayName(ping.motd(), config.host, config.port),
        plainMotd(ping.motd()),
        ping.online(),
        ping.playersOnline(),
        ping.playersMax());
  }

  private static String plainMotd(String motd) {
    if (motd == null) {
      return null;
    }
    String stripped = motd.replaceAll("(?i)§.", "").replace('\u0000', ' ').trim();
    return stripped.isEmpty() ? null : stripped;
  }

  private static int separator(String line) {
    int em = line.indexOf(" — ");
    if (em > 0) {
      return em;
    }
    int hyphen = line.indexOf(" - ");
    return hyphen > 0 ? hyphen : -1;
  }

  private record AllowedAddress(String host, int port) {}

  /** 테스트에서 허용 목록이 비었는지 구분하려고 둔 조회. 외부 응답에는 쓰지 않는다. */
  Set<String> sessionIds(String userId) {
    Map<String, ServerConfig> mine = sessions.get(userId);
    return mine == null ? Set.of() : Set.copyOf(mine.keySet());
  }
}
