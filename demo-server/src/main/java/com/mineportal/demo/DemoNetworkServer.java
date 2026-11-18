package com.mineportal.demo;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.SocketAddress;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import org.geysermc.mcprotocollib.network.server.NetworkServer;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

/**
 * One listener with a single boss thread and a single worker thread. Four instances are eight
 * daemon I/O threads. MCProtocolLib 26.1 names the hooks {@code createBossEventLoopGroup} and
 * {@code createWorkerEventLoopGroup}.
 */
final class DemoNetworkServer extends NetworkServer {
  DemoNetworkServer(SocketAddress address) {
    super(address, MinecraftProtocol::new);
  }

  @Override
  protected EventLoopGroup createBossEventLoopGroup() {
    return createBoss();
  }

  @Override
  protected EventLoopGroup createWorkerEventLoopGroup() {
    return createWorker();
  }

  private EventLoopGroup createBoss() {
    return TransportHelper.TRANSPORT_TYPE
        .eventLoopGroupFactory()
        .apply(1, new DefaultThreadFactory("demo-mc-io", true));
  }

  private EventLoopGroup createWorker() {
    return TransportHelper.TRANSPORT_TYPE
        .eventLoopGroupFactory()
        .apply(1, new DefaultThreadFactory("demo-mc-io", true));
  }
}
