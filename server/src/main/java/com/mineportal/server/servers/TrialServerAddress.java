package com.mineportal.server.servers;

import java.util.Locale;

/** 웹 체험 모드 allowlist와 비교할 때 쓰는 주소 정규화. 사용자가 입력한 host만 보고, DNS SRV로 해석된 대상 호스트는 비교하지 않는다. */
public final class TrialServerAddress {

  public static final int DEFAULT_PORT = 25565;

  private TrialServerAddress() {}

  /** 대소문자를 무시하고, 앞뒤 공백과 끝의 점을 뺀 호스트. 비어 있거나 호스트로 쓸 수 없으면 null. */
  public static String normalizeHost(String raw) {
    if (raw == null) {
      return null;
    }
    String host = raw.trim().toLowerCase(Locale.ROOT);
    if (host.startsWith("[") && host.endsWith("]") && host.length() >= 2) {
      host = host.substring(1, host.length() - 1).trim();
    }
    while (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    if (host.isEmpty() || host.length() > 253 || host.indexOf('\0') >= 0) {
      return null;
    }
    return host;
  }

  /** 빠졌거나 범위를 벗어난 사용자 입력 포트는 마인크래프트 기본 포트로 본다. */
  public static int userPort(int port) {
    if (port >= 1 && port <= 65535) {
      return port;
    }
    return DEFAULT_PORT;
  }

  /** 사용자 입력과 DB에 저장된 allowlist 행이 같은 주소인지. 저장된 포트가 유효하지 않으면 일치하지 않는다. */
  public static boolean matches(String userHost, int userPort, String storedHost, int storedPort) {
    String left = normalizeHost(userHost);
    String right = normalizeHost(storedHost);
    if (left == null || right == null || storedPort < 1 || storedPort > 65535) {
      return false;
    }
    return left.equals(right) && userPort(userPort) == storedPort;
  }
}
