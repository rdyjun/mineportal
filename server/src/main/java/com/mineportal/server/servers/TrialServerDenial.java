package com.mineportal.server.servers;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 웹 체험 모드 allowlist에 없는 주소에 대한 공통 거부 응답. */
public final class TrialServerDenial {

  public static final String CODE = "trial-server-not-allowed";

  public static final String MESSAGE = "웹 체험 모드에서는 허용된 서버에만 접속할 수 있어요. 다른 서버는 데스크톱 앱으로 접속해 주세요.";

  private TrialServerDenial() {}

  public static ResponseEntity<Map<String, String>> response() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", MESSAGE, "code", CODE));
  }
}
