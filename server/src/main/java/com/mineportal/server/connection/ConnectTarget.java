package com.mineportal.server.connection;

/** 이 PC에만 저장된 서버를 접속할 때 클라이언트가 보내는 주소. 서버는 이 값을 목록으로 저장하지 않는다. */
public record ConnectTarget(String host, Integer port, String version) {}
