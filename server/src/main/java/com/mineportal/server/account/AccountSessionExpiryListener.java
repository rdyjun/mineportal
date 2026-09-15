package com.mineportal.server.account;

import com.mineportal.server.connection.McConnectionManager;
import com.mineportal.server.desktop.DesktopConnectionManager;
import com.mineportal.server.servers.ServerRegistry;
import com.mineportal.server.ws.EventBroadcaster;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AccountSessionExpiryListener {
    private final ServerRegistry registry;
    private final McConnectionManager mcConnections;
    private final DesktopConnectionManager desktopConnections;
    private final EventBroadcaster broadcaster;

    public AccountSessionExpiryListener(ServerRegistry registry, McConnectionManager mcConnections,
                                        DesktopConnectionManager desktopConnections, EventBroadcaster broadcaster) {
        this.registry = registry;
        this.mcConnections = mcConnections;
        this.desktopConnections = desktopConnections;
        this.broadcaster = broadcaster;
    }

    @EventListener
    public void onExpired(ExpiredAccountSession expired) {
        Profile profile = expired.state().profile;
        if (profile != null) {
            for (var server : registry.listServers(profile.id())) {
                if (!"active".equals(server.phase)) continue;
                if (desktopConnections.isRouted(server.id)) {
                    desktopConnections.send(profile.id(), Map.of("type", "disconnect", "serverId", server.id));
                    desktopConnections.unmarkRouted(server.id);
                } else {
                    mcConnections.disconnect(server.id);
                }
            }
            desktopConnections.send(profile.id(), Map.of("type", "clear-account"));
        }
        broadcaster.refreshAccountFor(expired.sid());
        broadcaster.refreshServersFor(expired.sid());
    }
}
