package com.mineportal.server.servers;

import com.mineportal.server.account.AccountSessionManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/servers")
public class ServersController {

  private final ServerRegistry registry;
  private final AccountSessionManager accountSessions;

  public ServersController(ServerRegistry registry, AccountSessionManager accountSessions) {
    this.registry = registry;
    this.accountSessions = accountSessions;
  }

  private String ownerId(String sid) {
    var profile = accountSessions.get(sid).profile;
    return profile != null ? profile.id() : null;
  }

  /** 웹/앱 체험 모드의 고정 목록. 로그인하지 않아도 미리보기로 보여주고, 로그인하면 그 계정의 접속 상태를 함께 준다. */
  @GetMapping("/trial")
  public List<TrialServerView> trial(@RequestAttribute("sid") String sid) {
    return registry.listTrialServers(ownerId(sid));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@RequestAttribute("sid") String sid, @PathVariable String id) {
    String ownerId = ownerId(sid);
    if (ownerId == null) {
      return ResponseEntity.status(409)
          .body(
              Map.of(
                  "error",
                  "Log in with a Microsoft account to view this server",
                  "code",
                  "not-logged-in"));
    }
    Optional<ServerConfig> found = registry.getServer(id, ownerId);
    if (found.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "server not found"));
    }
    return ResponseEntity.ok(ServerSummary.of(found.get()));
  }
}
