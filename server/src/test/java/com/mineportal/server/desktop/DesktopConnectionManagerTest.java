package com.mineportal.server.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesktopConnectionManagerTest {

    @Test
    void reRegisteringSocketRemovesItsPreviousOwnerMapping() {
        DesktopConnectionManager manager = new DesktopConnectionManager();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);

        manager.register("owner-a", session);
        manager.register("owner-b", session);

        assertFalse(manager.isConnected("owner-a"));
        assertTrue(manager.isConnected("owner-b"));
        assertEquals("owner-b", manager.ownerIdOf(session));
    }

    @Test
    void issuingNewDeviceTokenRevokesPreviousTokenForOwner() {
        DesktopConnectionManager manager = new DesktopConnectionManager();

        String first = manager.issueDeviceToken("owner-a");
        String second = manager.issueDeviceToken("owner-a");

        assertNull(manager.ownerForDeviceToken(first));
        assertEquals("owner-a", manager.ownerForDeviceToken(second));
    }

    @Test
    void expiredDeviceTokenCannotResume() throws Exception {
        DesktopConnectionManager manager = new DesktopConnectionManager(1);
        String token = manager.issueDeviceToken("owner-a");
        Thread.sleep(5);

        assertNull(manager.ownerForDeviceToken(token));
    }

    @Test
    void unpairRevokesGrantAndRemovesConnectedOwner() throws Exception {
        DesktopConnectionManager manager = new DesktopConnectionManager();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        String token = manager.issueDeviceToken("owner-a");
        manager.register("owner-a", session);

        manager.unpair("owner-a");

        assertNull(manager.ownerForDeviceToken(token));
        assertFalse(manager.isConnected("owner-a"));
        assertNull(manager.ownerIdOf(session));
        verify(session).sendMessage(any());
    }
}
