package com.mineportal.demo;

/** Process-wide limits shared by every demo listener. */
public record DemoSettings(
    String bindHost,
    int maxPlayers,
    int maxMessageChars,
    int messageLimit,
    int messageWindowSeconds,
    int idleTimeoutSeconds,
    boolean botEnabled,
    int botIntervalSeconds) {
  public DemoSettings {
    if (bindHost == null || bindHost.isBlank()) {
      throw new IllegalArgumentException("bind host is required");
    }
    bindHost = bindHost.trim();
    maxPlayers = range("max players", maxPlayers, 1, 100);
    maxMessageChars = range("message length", maxMessageChars, 1, 256);
    messageLimit = range("message limit", messageLimit, 1, 30);
    messageWindowSeconds = range("message window", messageWindowSeconds, 1, 120);
    idleTimeoutSeconds = range("idle timeout", idleTimeoutSeconds, 1, 86400);
    botIntervalSeconds = range("bot interval", botIntervalSeconds, 1, 3600);
  }

  private static int range(String label, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
    }
    return value;
  }
}
