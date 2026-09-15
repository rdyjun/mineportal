package com.mineportal.server.account;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountSessionManager {

    private static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 12 * 60;

    private static final class SessionEntry {
        final AccountState state = new AccountState();
        volatile long lastAccessAt = System.currentTimeMillis();
    }

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    private final long idleTimeoutMs;

    public AccountSessionManager(ApplicationEventPublisher events) {
        this(events, Duration.ofMinutes(Long.parseLong(System.getenv()
                .getOrDefault("SESSION_IDLE_TIMEOUT_MINUTES", String.valueOf(DEFAULT_IDLE_TIMEOUT_MINUTES)))).toMillis());
    }

    AccountSessionManager(ApplicationEventPublisher events, long idleTimeoutMs) {
        this.events = events;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public AccountState get(String sid) {
        SessionEntry entry = sessions.computeIfAbsent(sid, k -> new SessionEntry());
        entry.lastAccessAt = System.currentTimeMillis();
        return entry.state;
    }

    public void remove(String sid) {
        sessions.remove(sid);
    }

    /** ownerId(로그인된 마인크래프트 프로필 id)로 세션을 역으로 찾는다 — 데스크톱 앱이
     * 페어링될 때, 그 계정이 이미 어느 브라우저 탭에서 로그인해뒀는지 확인하는 데 쓰인다. */
    public AccountState findByOwnerId(String ownerId) {
        for (SessionEntry entry : sessions.values()) {
            AccountState state = entry.state;
            if (state.profile != null && ownerId.equals(state.profile.id())) {
                entry.lastAccessAt = System.currentTimeMillis();
                return state;
            }
        }
        return null;
    }

    @Scheduled(fixedDelayString = "${SESSION_CLEANUP_INTERVAL_MS:60000}")
    void removeExpiredSessions() {
        long cutoff = System.currentTimeMillis() - idleTimeoutMs;
        var expired = new ArrayList<ExpiredAccountSession>();
        sessions.forEach((sid, entry) -> {
            if (entry.lastAccessAt <= cutoff && sessions.remove(sid, entry)) {
                expired.add(new ExpiredAccountSession(sid, entry.state));
            }
        });
        expired.forEach(events::publishEvent);
    }

}
