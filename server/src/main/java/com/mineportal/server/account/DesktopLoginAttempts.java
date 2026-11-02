package com.mineportal.server.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One-use browser-to-app handoff; the polling proof never appears in a URL. */
final class DesktopLoginAttempts {
  private static final long TTL_MS = 10 * 60 * 1000;
  private final Clock clock;
  private final Map<String, Attempt> attempts = new HashMap<>();

  record Started(String id, String proof) {}

  private static final class Attempt {
    final String appSid;
    final String proof;
    final long expiresAt;
    String browserSid;
    String confirmation;
    boolean authorized;
    String completedSid;

    Attempt(String appSid, String proof, long expiresAt) {
      this.appSid = appSid;
      this.proof = proof;
      this.expiresAt = expiresAt;
    }
  }

  DesktopLoginAttempts() {
    this(Clock.systemUTC());
  }

  DesktopLoginAttempts(Clock clock) {
    this.clock = clock;
  }

  synchronized Started start(String appSid) {
    attempts.values().removeIf(a -> a.expiresAt <= clock.millis() || a.appSid.equals(appSid));
    if (attempts.size() >= 1000) throw new IllegalStateException("Too many login attempts");
    String id = UUID.randomUUID().toString();
    String proof = UUID.randomUUID().toString() + UUID.randomUUID();
    attempts.put(id, new Attempt(appSid, proof, clock.millis() + TTL_MS));
    return new Started(id, proof);
  }

  synchronized String prepare(String id, String browserSid) {
    Attempt attempt = active(id);
    if (attempt.authorized) throw new IllegalArgumentException("Already started");
    attempt.browserSid = browserSid;
    attempt.confirmation = UUID.randomUUID().toString();
    return attempt.confirmation;
  }

  synchronized void authorize(String id, String browserSid, String confirmation) {
    Attempt attempt = active(id);
    if (attempt.authorized
        || !browserSid.equals(attempt.browserSid)
        || !matches(attempt.confirmation, confirmation)) {
      throw new IllegalArgumentException("Invalid confirmation");
    }
    attempt.authorized = true;
    attempt.confirmation = null;
  }

  synchronized void complete(String id, String sid) {
    Attempt attempt = active(id);
    if (!attempt.authorized || attempt.completedSid != null) {
      throw new IllegalArgumentException("Invalid completion");
    }
    attempt.completedSid = sid;
  }

  /** Null means pending; success consumes the proof and returns only the rotated session id. */
  synchronized String claim(String id, String appSid, String proof) {
    Attempt attempt = active(id);
    if (!attempt.appSid.equals(appSid) || !matches(attempt.proof, proof)) {
      throw new IllegalArgumentException("Invalid proof");
    }
    if (attempt.completedSid == null) return null;
    attempts.remove(id);
    return attempt.completedSid;
  }

  private Attempt active(String id) {
    Attempt attempt = attempts.get(id);
    if (attempt == null || attempt.expiresAt <= clock.millis()) {
      attempts.remove(id);
      throw new IllegalArgumentException("Expired login attempt");
    }
    return attempt;
  }

  private static boolean matches(String expected, String supplied) {
    return expected != null
        && supplied != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }
}
