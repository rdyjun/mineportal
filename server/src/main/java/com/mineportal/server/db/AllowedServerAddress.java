package com.mineportal.server.db;

/** trial_allowed_servers의 한 행. host는 사용자가 입력한 주소이며 SRV로 풀린 대상이 아니다. */
public record AllowedServerAddress(String host, int port) {}
