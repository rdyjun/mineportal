package com.mineportal.server.desktop;

import com.mineportal.server.account.AccountSessionManager;
import com.mineportal.server.servers.ServerRegistry;
import com.mineportal.server.ws.EventBroadcaster;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DesktopPairController {

    private final AccountSessionManager accountSessions;
    private final DesktopConnectionManager desktopConnections;
    private final ServerRegistry registry;
    private final EventBroadcaster broadcaster;

    public DesktopPairController(AccountSessionManager accountSessions, DesktopConnectionManager desktopConnections,
                                 ServerRegistry registry, EventBroadcaster broadcaster) {
        this.accountSessions = accountSessions;
        this.desktopConnections = desktopConnections;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    /** All device management is initiated from the website; the native agent has no UI. */
    @PostMapping("/api/desktop/unpair")
    public ResponseEntity<?> unpair(@RequestAttribute("sid") String sid) {
        var profile = accountSessions.get(sid).profile;
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Log in before unlinking the desktop app"));
        }
        for (var server : registry.listServers(profile.id())) {
            if (desktopConnections.isRouted(server.id)) {
                desktopConnections.send(profile.id(), Map.of("type", "disconnect", "serverId", server.id));
                desktopConnections.unmarkRouted(server.id);
                server.phase = "closed";
                server.connected = false;
                broadcaster.setStatus(server, "desktop app unlinked", true);
            }
        }
        desktopConnections.unpair(profile.id());
        broadcaster.refreshAccountForOwner(profile.id());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 로그인된 브라우저 세션이 데스크톱 앱과 연동할 1회용 코드를 요청한다. */
    @PostMapping("/api/desktop/pair")
    public ResponseEntity<?> pair(@RequestAttribute("sid") String sid) {
        var profile = accountSessions.get(sid).profile;
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Log in before pairing the desktop app"));
        }
        String code = desktopConnections.createPairingCode(profile.id());
        return ResponseEntity.ok(Map.of("code", code, "expiresInSeconds", 300));
    }

}
