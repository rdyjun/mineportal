package com.mineportal.server.desktop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mineportal.server.account.AccountSessionManager;
import com.mineportal.server.servers.ServerConfig;
import com.mineportal.server.servers.ServerRegistry;
import com.mineportal.server.ws.EventBroadcaster;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class DesktopWsHandlerTest {
  private final DesktopConnectionManager desktop = new DesktopConnectionManager();
  private final ServerRegistry registry = mock(ServerRegistry.class);
  private final EventBroadcaster broadcaster = mock(EventBroadcaster.class);
  private final DesktopWsHandler handler =
      new DesktopWsHandler(desktop, mock(AccountSessionManager.class), registry, broadcaster);
  private final ServerConfig server = new ServerConfig("server-a", "example.test", 25565, "auto");

  private WebSocketSession socket() {
    WebSocketSession socket = mock(WebSocketSession.class);
    when(socket.getAttributes()).thenReturn(new HashMap<>());
    when(socket.isOpen()).thenReturn(true);
    return socket;
  }

  private long route() {
    long attempt = server.beginConnection();
    desktop.markRouted(server.id, attempt);
    when(registry.getServer(server.id, "owner-a")).thenReturn(Optional.of(server));
    return attempt;
  }

  private void disconnect(WebSocketSession socket, long attempt) throws Exception {
    handler.handleTextMessage(
        socket,
        new TextMessage(
            "{\"type\":\"disconnected\",\"serverId\":\"server-a\",\"attemptId\":" + attempt + "}"));
  }

  @Test
  void unauthenticatedSocketCannotRemoveRouting() throws Exception {
    long attempt = route();
    disconnect(socket(), attempt);
    assertEquals(attempt, desktop.routedAttempt(server.id));
    assertTrue(server.isActive());
    verifyNoInteractions(registry, broadcaster);
  }

  @Test
  void anotherOwnerCannotRemoveRouting() throws Exception {
    long attempt = route();
    WebSocketSession attacker = socket();
    desktop.register("owner-b", attacker);
    when(registry.getServer(server.id, "owner-b")).thenReturn(Optional.empty());
    disconnect(attacker, attempt);
    assertEquals(attempt, desktop.routedAttempt(server.id));
    assertTrue(server.isActive());
    verifyNoInteractions(broadcaster);
  }

  @Test
  void replacedSocketCannotMutateServerOrInjectChat() throws Exception {
    long attempt = route();
    WebSocketSession old = socket();
    desktop.register("owner-a", old);
    desktop.register("owner-a", socket());
    disconnect(old, attempt);
    handler.handleTextMessage(
        old,
        new TextMessage(
            "{\"type\":\"connected\",\"serverId\":\"server-a\",\"attemptId\":" + attempt + "}"));
    handler.handleTextMessage(
        old,
        new TextMessage("{\"type\":\"chat\",\"serverId\":\"server-a\",\"message\":\"injected\"}"));
    assertEquals(attempt, desktop.routedAttempt(server.id));
    assertFalse(server.connected());
    verifyNoInteractions(registry, broadcaster);
  }

  @Test
  void currentOwnerCanCloseOwnAttempt() throws Exception {
    long attempt = route();
    WebSocketSession current = socket();
    desktop.register("owner-a", current);
    disconnect(current, attempt);
    assertFalse(desktop.isRouted(server.id));
    assertFalse(server.isActive());
    verify(broadcaster).setStatus(server, "disconnected: ", true);
  }

  @Test
  void missingServerIdIsIgnored() throws Exception {
    WebSocketSession current = socket();
    desktop.register("owner-a", current);
    handler.handleTextMessage(
        current, new TextMessage("{\"type\":\"disconnected\",\"attemptId\":1}"));
    verifyNoInteractions(registry, broadcaster);
  }
}
