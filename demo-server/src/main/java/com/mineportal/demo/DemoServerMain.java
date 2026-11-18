package com.mineportal.demo;

import org.geysermc.mcprotocollib.auth.SessionService;

/** Process entry point. One JVM, one real Mojang SessionService, up to four listeners. */
public final class DemoServerMain {
  private DemoServerMain() {}

  public static void main(String[] args) {
    final DemoConfig config;
    try {
      config = DemoConfig.fromEnvironment();
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }
    DemoServers servers = DemoServers.start(config, new SessionService());
    Runtime.getRuntime().addShutdownHook(new Thread(servers::close, "demo-mc-shutdown"));
    try {
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      servers.close();
    }
  }
}
