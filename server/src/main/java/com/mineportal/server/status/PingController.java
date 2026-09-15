package com.mineportal.server.status;

import com.mineportal.server.account.AccountSessionManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {

    private final PingService pingService;
    private final AccountSessionManager accountSessions;

    public PingController(PingService pingService, AccountSessionManager accountSessions) {
        this.pingService = pingService;
        this.accountSessions = accountSessions;
    }

    @GetMapping("/api/ping")
    public ResponseEntity<?> ping(@RequestAttribute("sid") String sid, @RequestParam String host, @RequestParam int port) {
        if (accountSessions.get(sid).profile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "login required"));
        }
        if (host.isBlank() || port < 1 || port > 65535) {
            return ResponseEntity.badRequest().body(Map.of("error", "host and a valid port are required"));
        }
        try {
            return ResponseEntity.ok(pingService.ping(host, port));
        } catch (java.net.UnknownHostException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "cannot ping private/internal or unresolved hosts"));
        }
    }

}
