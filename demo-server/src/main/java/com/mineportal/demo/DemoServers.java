package com.mineportal.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts every configured listener and the single tick thread they share. */
public final class DemoServers implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DemoServers.class);

  private final List<DemoInstance> instances;
  private final ScheduledExecutorService scheduler;
  private boolean closed;

  private DemoServers(List<DemoInstance> instances, ScheduledExecutorService scheduler) {
    this.instances = instances;
    this.scheduler = scheduler;
  }

  public static DemoServers start(DemoConfig config, SessionService sessionService) {
    LOGGER.info(
        "Online mode is on. {} demo server(s) authenticate with the relay SessionService"
            + " (Mojang hasJoined).",
        config.servers().size());
    ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "demo-mc-tick");
              thread.setDaemon(true);
              return thread;
            });
    List<DemoInstance> started = new ArrayList<>();
    try {
      for (DemoServerSpec spec : config.servers()) {
        started.add(DemoInstance.start(spec, config.settings(), sessionService, scheduler));
      }
    } catch (RuntimeException failure) {
      for (DemoInstance instance : started) {
        instance.close();
      }
      scheduler.shutdownNow();
      throw failure;
    }
    return new DemoServers(List.copyOf(started), scheduler);
  }

  @Override
  public void close() {
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
    }
    for (DemoInstance instance : instances) {
      instance.close();
    }
    scheduler.shutdownNow();
  }
}
