package com.mineportal.server.servers;

/** 체험하기에 보여주는 제공 서버 한 줄. id는 이 브라우저 계정의 메모리 안 접속 상태이고, 사용자 서버 목록(DB)에는 없다. */
public record TrialServerView(
    String id,
    String host,
    int port,
    String version,
    String status,
    String phase,
    boolean connected,
    String displayName,
    String motd,
    boolean online,
    Integer playersOnline,
    Integer playersMax) {}
