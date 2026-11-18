package com.mineportal.demo;

/** One chat-only Minecraft listener: a display name and a TCP port. */
public record DemoServerSpec(String name, int port) {
  public DemoServerSpec {
    if (name == null) {
      throw new IllegalArgumentException("server name is required");
    }
    name = name.trim();
    int points = name.codePointCount(0, name.length());
    if (points < 1 || points > 32) {
      throw new IllegalArgumentException("server name must be 1 to 32 characters");
    }
    if (name.codePoints().anyMatch(codePoint -> codePoint < 0x20 || codePoint == 0x7f)) {
      throw new IllegalArgumentException("server name contains a control character");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535");
    }
  }
}
