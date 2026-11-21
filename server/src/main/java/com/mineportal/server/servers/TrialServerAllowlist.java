package com.mineportal.server.servers;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 웹 체험 릴레이가 접속할 수 있는 마인크래프트 주소. 설정이 비어 있으면 어떤 주소도 허용하지 않는다. 값을 아예 주지 않으면 데모 서버 네 대를 기본으로 쓴다. */
@Component
public class TrialServerAllowlist {

  public static final String DEFAULT_SERVERS =
      "demo-mc:25565,demo-mc:25566,demo-mc:25567,demo-mc:25568";

  private final List<Entry> entries;

  public TrialServerAllowlist(
      @Value("${mineportal.trial-servers:" + DEFAULT_SERVERS + "}") String configured) {
    this.entries = List.copyOf(parse(configured));
  }

  public List<Entry> entries() {
    return entries;
  }

  public boolean isAllowed(String host, int port) {
    for (Entry entry : entries) {
      if (TrialServerAddress.matches(host, port, entry.host(), entry.port())) {
        return true;
      }
    }
    return false;
  }

  static List<Entry> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<Entry> parsed = new ArrayList<>();
    for (String part : raw.split(",")) {
      String item = part.trim();
      if (item.isEmpty()) {
        continue;
      }
      int split = item.lastIndexOf(':');
      String hostPart = split <= 0 ? item : item.substring(0, split);
      int port = TrialServerAddress.DEFAULT_PORT;
      if (split > 0) {
        try {
          port = Integer.parseInt(item.substring(split + 1).trim());
        } catch (NumberFormatException ignored) {
          continue;
        }
      }
      String host = TrialServerAddress.normalizeHost(hostPart);
      if (host == null || port < 1 || port > 65535) {
        continue;
      }
      parsed.add(new Entry(host, port));
    }
    return parsed;
  }

  public record Entry(String host, int port) {}
}
