package com.mineportal.demo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Servers and limits read from the process environment. */
public record DemoConfig(List<DemoServerSpec> servers, DemoSettings settings) {
  public static final int MAX_SERVERS = 4;

  public DemoConfig {
    if (servers == null || servers.isEmpty() || servers.size() > MAX_SERVERS) {
      throw new IllegalArgumentException("DEMO_SERVERS must list 1 to " + MAX_SERVERS + " servers");
    }
    if (settings == null) {
      throw new IllegalArgumentException("settings are required");
    }
    servers = List.copyOf(servers);
    Set<Integer> ports = new HashSet<>();
    Set<String> names = new HashSet<>();
    for (DemoServerSpec server : servers) {
      if (!ports.add(server.port())) {
        throw new IllegalArgumentException("duplicate port " + server.port());
      }
      if (!names.add(server.name().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("duplicate server name " + server.name());
      }
    }
  }

  public static DemoConfig fromEnvironment() {
    return fromEnvironment(System.getenv());
  }

  public static DemoConfig fromEnvironment(Map<String, String> env) {
    return new DemoConfig(parseServers(env.get("DEMO_SERVERS")), parseSettings(env));
  }

  private static List<DemoServerSpec> parseServers(String raw) {
    if (raw == null) {
      return List.of(new DemoServerSpec("체험", 25565));
    }
    if (raw.isBlank()) {
      throw new IllegalArgumentException("DEMO_SERVERS is blank");
    }
    String[] parts = raw.split(",", -1);
    if (parts.length > MAX_SERVERS) {
      throw new IllegalArgumentException("DEMO_SERVERS must list 1 to " + MAX_SERVERS + " servers");
    }
    List<DemoServerSpec> servers = new ArrayList<>();
    for (String part : parts) {
      String entry = part.trim();
      if (entry.isEmpty()) {
        throw new IllegalArgumentException("DEMO_SERVERS contains an empty entry");
      }
      int colon = entry.lastIndexOf(':');
      if (colon <= 0 || colon == entry.length() - 1) {
        throw new IllegalArgumentException("DEMO_SERVERS entry must be name:port");
      }
      String name = entry.substring(0, colon).trim();
      String portText = entry.substring(colon + 1).trim();
      int port;
      try {
        port = Integer.parseInt(portText);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid port in DEMO_SERVERS", e);
      }
      servers.add(new DemoServerSpec(name, port));
    }
    return servers;
  }

  private static DemoSettings parseSettings(Map<String, String> env) {
    return new DemoSettings(
        text(env, "DEMO_BIND_HOST", "0.0.0.0"),
        integer(env, "DEMO_MAX_PLAYERS", 16),
        integer(env, "DEMO_MAX_MESSAGE_CHARS", 256),
        integer(env, "DEMO_MESSAGE_LIMIT", 5),
        integer(env, "DEMO_MESSAGE_WINDOW_SECONDS", 10),
        integer(env, "DEMO_IDLE_TIMEOUT_SECONDS", 600),
        bool(env, "DEMO_BOT_ENABLED", false),
        integer(env, "DEMO_BOT_INTERVAL_SECONDS", 60));
  }

  private static String text(Map<String, String> env, String key, String defaultValue) {
    String raw = env.get(key);
    if (raw == null) {
      return defaultValue;
    }
    if (raw.isBlank()) {
      throw new IllegalArgumentException(key + " is blank");
    }
    return raw.trim();
  }

  private static int integer(Map<String, String> env, String key, int defaultValue) {
    String raw = env.get(key);
    if (raw == null) {
      return defaultValue;
    }
    if (raw.isBlank()) {
      throw new IllegalArgumentException(key + " is blank");
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " must be an integer", e);
    }
  }

  static boolean bool(Map<String, String> env, String key, boolean defaultValue) {
    String raw = env.get(key);
    if (raw == null) {
      return defaultValue;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return switch (value) {
      case "1", "true", "yes", "y", "on" -> true;
      case "0", "false", "no", "n", "off" -> false;
      default -> throw new IllegalArgumentException(key + " must be a boolean");
    };
  }
}
