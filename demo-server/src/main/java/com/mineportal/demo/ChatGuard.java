package com.mineportal.demo;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/**
 * Per-player message filter. Length is checked before the rate limit and does not spend a token. A
 * run of the same rejection warns once, then stays quiet until a message is accepted.
 */
public final class ChatGuard {
  public enum Decision {
    ACCEPT,
    BLANK,
    TOO_LONG,
    TOO_FAST,
    SILENT
  }

  private final int maxChars;
  private final int limit;
  private final long windowMillis;
  private final long idleMillis;
  private final LongSupplier clock;
  private final ArrayDeque<Long> hits = new ArrayDeque<>();
  private long lastActivity;
  private Decision streak;

  public ChatGuard(DemoSettings settings) {
    this(settings, System::currentTimeMillis);
  }

  ChatGuard(DemoSettings settings, LongSupplier clock) {
    this.maxChars = settings.maxMessageChars();
    this.limit = settings.messageLimit();
    this.windowMillis = settings.messageWindowSeconds() * 1000L;
    this.idleMillis = settings.idleTimeoutSeconds() * 1000L;
    this.clock = clock;
    this.lastActivity = clock.getAsLong();
  }

  public synchronized Decision admit(String raw) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      return Decision.BLANK;
    }
    if (text.codePointCount(0, text.length()) > maxChars) {
      return warn(Decision.TOO_LONG);
    }
    long now = clock.getAsLong();
    while (!hits.isEmpty() && now - hits.peekFirst() >= windowMillis) {
      hits.removeFirst();
    }
    if (hits.size() >= limit) {
      return warn(Decision.TOO_FAST);
    }
    hits.addLast(now);
    streak = null;
    lastActivity = now;
    return Decision.ACCEPT;
  }

  public synchronized void touch() {
    lastActivity = clock.getAsLong();
  }

  public synchronized boolean idle() {
    return clock.getAsLong() - lastActivity >= idleMillis;
  }

  private Decision warn(Decision decision) {
    if (streak == decision) {
      return Decision.SILENT;
    }
    streak = decision;
    return decision;
  }
}
