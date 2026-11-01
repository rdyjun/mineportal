package com.mineportal.server.servers;

import com.mineportal.server.connection.ChatMessage;
import com.mineportal.server.status.StatusEntry;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 저장된 서버 하나에 대한 인메모리 런타임 상태로, ServerRegistry에서 id로 키가 매겨진다. 시작 시 DB(host/port/version)로부터 복원되며,
 * status/phase/connected/messages는 프로세스 재시작 시 살아있는 연결이 유지될 수 없으므로 새로 초기화된다. src/servers.ts의
 * ServerConfig를 포팅한 것.
 */
public class ServerConfig {

  private static final int MAX_HISTORY = 200;

  public final String id;
  public final String host;
  public final int port;
  public final String version;

  /** 자유 형식의 표시용 상태 문자열 (클라이언트로부터 오거나, 아무 시도도 없었으면 "idle"). */
  public volatile String status = "idle";

  /** 연결 가능 여부와 실제 접속 여부의 단일 진실 원천. status는 표시용 텍스트일 뿐이다. */
  private volatile ConnectionState connectionState = ConnectionState.IDLE;

  private long lastAttemptId;

  private final List<StatusEntry> statusHistory = Collections.synchronizedList(new LinkedList<>());
  private final List<ChatMessage> messages = Collections.synchronizedList(new LinkedList<>());

  public ServerConfig(String id, String host, int port, String version) {
    this.id = id;
    this.host = host;
    this.port = port;
    this.version = version;
  }

  /** 동시에 들어온 요청 중 하나만 새 연결 시도를 시작하게 한다. */
  public synchronized long beginConnection() {
    if (connectionState.active()) return 0;
    connectionState = ConnectionState.CONNECTING;
    return ++lastAttemptId;
  }

  /** 늦게 도착한 connected 이벤트가 이미 닫힌 연결을 되살리지 못하게 한다. */
  public synchronized boolean markConnected(long attemptId) {
    if (attemptId != lastAttemptId || connectionState != ConnectionState.CONNECTING) return false;
    connectionState = ConnectionState.CONNECTED;
    return true;
  }

  public synchronized boolean markFailed(long attemptId) {
    if (attemptId != lastAttemptId || !connectionState.active()) return false;
    connectionState = ConnectionState.FAILED;
    return true;
  }

  public synchronized boolean markClosed(long attemptId) {
    if (attemptId != lastAttemptId || connectionState == ConnectionState.CLOSED) return false;
    connectionState = ConnectionState.CLOSED;
    return true;
  }

  public synchronized boolean markClosed() {
    if (connectionState == ConnectionState.CLOSED) return false;
    connectionState = ConnectionState.CLOSED;
    return true;
  }

  public ConnectionState connectionState() {
    return connectionState;
  }

  public synchronized long currentAttemptId() {
    return lastAttemptId;
  }

  public synchronized boolean isCurrentAttempt(long attemptId) {
    return attemptId == lastAttemptId && connectionState.active();
  }

  public boolean isActive() {
    return connectionState.active();
  }

  public String phase() {
    return connectionState.phase();
  }

  public boolean connected() {
    return connectionState.connected();
  }

  public void pushStatusHistory(StatusEntry entry) {
    statusHistory.add(entry);
    if (statusHistory.size() > MAX_HISTORY) statusHistory.remove(0);
  }

  public void pushMessage(ChatMessage message) {
    messages.add(message);
    if (messages.size() > MAX_HISTORY) messages.remove(0);
  }

  public List<StatusEntry> getStatusHistory() {
    return List.copyOf(statusHistory);
  }

  public List<ChatMessage> getMessages() {
    return List.copyOf(messages);
  }
}
