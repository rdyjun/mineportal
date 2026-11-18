package com.mineportal.server.servers;

/** 웹 체험 모드 allowlist에 없는 주소로 서버를 추가하거나 릴레이 접속하려 할 때. */
public class TrialServerNotAllowedException extends RuntimeException {

  public TrialServerNotAllowedException() {
    super(TrialServerDenial.MESSAGE);
  }
}
