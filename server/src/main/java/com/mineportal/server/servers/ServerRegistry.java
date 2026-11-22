package com.mineportal.server.servers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 접속에 필요한 서버 상태만 메모리에 둔다. 사용자가 저장한 서버 목록은 이 PC의 클라이언트에 있고, 여기에는 기록하지 않는다. 체험 서버는 카탈로그가 계정별로 만든다.
 */
@Component
public class ServerRegistry {

  private final TrialServerCatalog trialCatalog;
  private final Map<String, Map<String, ServerConfig>> sessions = new ConcurrentHashMap<>();

  public ServerRegistry(TrialServerCatalog trialCatalog) {
    this.trialCatalog = trialCatalog;
  }

  /** 이 계정이 지금 접속하려는 주소. 같은 id가 이미 있으면 그 설정을 그대로 쓴다. 목록 저장이 아니라, 끊기 전까지 연결 상태를 붙들기 위한 자리다. */
  public ServerConfig openSession(String userId, String id, String host, int port, String version) {
    if (userId == null || id == null || id.isBlank() || id.length() > 80) {
      throw new IllegalArgumentException("server id is required");
    }
    String normalized = TrialServerAddress.normalizeHost(host);
    if (normalized == null || port < 1 || port > 65535) {
      throw new IllegalArgumentException("host and port are required");
    }
    String resolvedVersion = version == null || version.isBlank() ? "auto" : version.trim();
    if (resolvedVersion.length() > 64) {
      throw new IllegalArgumentException("version is too long");
    }
    Optional<ServerConfig> trial = trialCatalog.find(userId, id);
    if (trial.isPresent()) {
      return trial.get();
    }
    Map<String, ServerConfig> mine =
        sessions.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
    ServerConfig existing = mine.get(id);
    if (existing != null) {
      return existing;
    }
    ServerConfig created = new ServerConfig(id, normalized, port, resolvedVersion);
    mine.put(id, created);
    return created;
  }

  public Optional<ServerConfig> getServer(String id, String userId) {
    Optional<ServerConfig> trial = trialCatalog.find(userId, id);
    if (trial.isPresent()) {
      return trial;
    }
    if (userId == null || id == null) {
      return Optional.empty();
    }
    Map<String, ServerConfig> mine = sessions.get(userId);
    if (mine == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mine.get(id));
  }

  public boolean isOwnedByUser(String serverId, String userId) {
    return getServer(serverId, userId).isPresent();
  }

  /** 로그아웃이나 데스크톱 연결 해제 때 끊어야 하는, 이 계정의 살아있는 서버. */
  public List<ServerConfig> listRuntimeServers(String userId) {
    List<ServerConfig> all = new ArrayList<>(trialCatalog.live(userId));
    Map<String, ServerConfig> mine = userId == null ? null : sessions.get(userId);
    if (mine != null) {
      all.addAll(mine.values());
    }
    return all;
  }

  public void releaseTrialServers(String userId) {
    trialCatalog.forget(userId);
    if (userId != null) {
      sessions.remove(userId);
    }
  }

  public List<TrialServerView> listTrialServers(String userId) {
    return trialCatalog.list(userId);
  }
}
