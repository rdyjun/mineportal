package com.mineportal.demo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manual resource probe. Not a JUnit test. Four in-process instances, bot off, no Mojang traffic.
 */
public final class UsageProbe {
  private UsageProbe() {}

  public static void main(String[] args) throws Exception {
    RecordingSessionService sessions = new RecordingSessionService();
    List<DemoServerSpec> specs = new ArrayList<>();
    int base = 25565;
    for (int i = 0; i < DemoConfig.MAX_SERVERS; i++) {
      specs.add(new DemoServerSpec("probe-" + i, base + i));
    }
    DemoSettings settings = new DemoSettings("127.0.0.1", 16, 256, 5, 10, 600, false, 60);
    DemoServers servers = DemoServers.start(new DemoConfig(specs, settings), sessions);
    try {
      Thread.sleep(3_000);
      Sample idle = sample(10_000);
      System.out.println("idle " + idle);

      List<McTestClient> clients = new ArrayList<>();
      try {
        for (int i = 0; i < 8; i++) {
          int port = base + (i % DemoConfig.MAX_SERVERS);
          clients.add(McTestClient.connect("127.0.0.1", port, "Player" + i, sessions));
        }
        for (McTestClient client : clients) {
          client.awaitPlay();
        }
        long chatStart = System.nanoTime();
        Sample chat = sampleDuringChat(clients, 15_000);
        long elapsedMs = (System.nanoTime() - chatStart) / 1_000_000L;
        System.out.println("chat " + elapsedMs + "ms " + chat);
      } finally {
        for (McTestClient client : clients) {
          client.close();
        }
      }
    } finally {
      servers.close();
    }
  }

  private static Sample sampleDuringChat(List<McTestClient> clients, long millis) throws Exception {
    long end = System.currentTimeMillis() + millis;
    long cpuStart = processCpuTicks();
    long wallStart = System.nanoTime();
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    long heapUsed = 0;
    long heapCommittedMax = 0;
    long nonHeapUsed = 0;
    long rss = 0;
    long rssMax = 0;
    int samples = 0;
    int round = 0;
    while (System.currentTimeMillis() < end) {
      for (McTestClient client : clients) {
        client.chat("round " + round);
      }
      round++;
      Sample once = readOnce(memory);
      samples++;
      heapUsed += once.heapUsed;
      nonHeapUsed += once.nonHeapUsed;
      rss += once.rssAvg;
      heapCommittedMax = Math.max(heapCommittedMax, once.heapCommittedMax);
      rssMax = Math.max(rssMax, once.rssMax);
      Thread.sleep(Math.max(1, millis / 15));
    }
    long cpu = processCpuTicks() - cpuStart;
    long wall = System.nanoTime() - wallStart;
    return new Sample(
        cpuPercent(cpu, wall),
        samples == 0 ? 0 : rss / samples,
        rssMax,
        hwmKb(),
        samples == 0 ? 0 : heapUsed / samples,
        heapCommittedMax,
        samples == 0 ? 0 : nonHeapUsed / samples,
        ManagementFactory.getThreadMXBean().getThreadCount());
  }

  private static Sample sample(long millis) throws Exception {
    long cpuStart = processCpuTicks();
    long wallStart = System.nanoTime();
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    long heapUsed = 0;
    long heapCommittedMax = 0;
    long nonHeapUsed = 0;
    long rss = 0;
    long rssMax = 0;
    int samples = 0;
    long end = System.currentTimeMillis() + millis;
    while (System.currentTimeMillis() < end) {
      Sample once = readOnce(memory);
      samples++;
      heapUsed += once.heapUsed;
      nonHeapUsed += once.nonHeapUsed;
      rss += once.rssAvg;
      heapCommittedMax = Math.max(heapCommittedMax, once.heapCommittedMax);
      rssMax = Math.max(rssMax, once.rssMax);
      Thread.sleep(200);
    }
    long cpu = processCpuTicks() - cpuStart;
    long wall = System.nanoTime() - wallStart;
    return new Sample(
        cpuPercent(cpu, wall),
        samples == 0 ? 0 : rss / samples,
        rssMax,
        hwmKb(),
        samples == 0 ? 0 : heapUsed / samples,
        heapCommittedMax,
        samples == 0 ? 0 : nonHeapUsed / samples,
        ManagementFactory.getThreadMXBean().getThreadCount());
  }

  private static Sample readOnce(MemoryMXBean memory) throws Exception {
    long rssKb = rssKb();
    return new Sample(
        0,
        rssKb,
        rssKb,
        hwmKb(),
        memory.getHeapMemoryUsage().getUsed(),
        memory.getHeapMemoryUsage().getCommitted(),
        memory.getNonHeapMemoryUsage().getUsed(),
        ManagementFactory.getThreadMXBean().getThreadCount());
  }

  private static long processCpuTicks() throws Exception {
    String stat = Files.readString(Path.of("/proc/self/stat"));
    int end = stat.lastIndexOf(')');
    String[] fields = stat.substring(end + 2).split(" ");
    return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
  }

  private static double cpuPercent(long cpuTicks, long wallNanos) {
    if (wallNanos <= 0) {
      return 0;
    }
    double cpuSeconds = cpuTicks / 100.0;
    double wallSeconds = wallNanos / 1_000_000_000.0;
    return (cpuSeconds * 100.0) / wallSeconds;
  }

  private static long rssKb() throws Exception {
    return statusKb("VmRSS:");
  }

  private static long hwmKb() throws Exception {
    return statusKb("VmHWM:");
  }

  private static long statusKb(String label) throws Exception {
    for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
      if (line.startsWith(label)) {
        String[] parts = line.trim().split("\\s+");
        return Long.parseLong(parts[1]);
      }
    }
    return 0;
  }

  private record Sample(
      double cpuPercent,
      long rssAvg,
      long rssMax,
      long hwm,
      long heapUsed,
      long heapCommittedMax,
      long nonHeapUsed,
      int threads) {
    @Override
    public String toString() {
      return String.format(
          Locale.ROOT,
          "cpu=%.2f%% rssAvg=%dKB rssMax=%dKB hwm=%dKB heapUsed=%d heapCommittedMax=%d nonHeap=%d threads=%d",
          cpuPercent,
          rssAvg,
          rssMax,
          hwm,
          heapUsed,
          heapCommittedMax,
          nonHeapUsed,
          threads);
    }
  }
}
