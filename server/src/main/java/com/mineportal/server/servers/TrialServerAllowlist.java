package com.mineportal.server.servers;

import com.mineportal.server.db.AllowedServerAddress;
import com.mineportal.server.db.ServerRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** 웹 체험 모드(백엔드 릴레이)에서 접속할 수 있는 마인크래프트 주소. 테이블이 비어 있거나 조회에 실패하면 어떤 주소도 허용하지 않는다. */
@Component
public class TrialServerAllowlist {

  private final ServerRepository repository;

  public TrialServerAllowlist(ServerRepository repository) {
    this.repository = repository;
  }

  public boolean isAllowed(String host, int port) {
    List<AllowedServerAddress> rows;
    try {
      rows = repository.listTrialAllowedServers();
    } catch (RuntimeException failure) {
      System.err.println("trial allowlist lookup failed: " + failure.getMessage());
      return false;
    }
    if (rows == null || rows.isEmpty()) {
      return false;
    }
    for (AllowedServerAddress row : rows) {
      if (row != null && TrialServerAddress.matches(host, port, row.host(), row.port())) {
        return true;
      }
    }
    return false;
  }
}
