package com.mineportal.server.servers;

/**
 * Authoritative lifecycle for one server connection.
 *
 * <p>The public API historically exposes a coarse {@code phase} plus a {@code connected} flag. Both
 * values are now derived from this state so contradictory combinations cannot be created.
 */
public enum ConnectionState {
  IDLE("idle", false, false),
  CONNECTING("active", false, true),
  CONNECTED("active", true, true),
  FAILED("closed", false, false),
  CLOSED("closed", false, false);

  private final String phase;
  private final boolean connected;
  private final boolean active;

  ConnectionState(String phase, boolean connected, boolean active) {
    this.phase = phase;
    this.connected = connected;
    this.active = active;
  }

  public String phase() {
    return phase;
  }

  public boolean connected() {
    return connected;
  }

  public boolean active() {
    return active;
  }
}
