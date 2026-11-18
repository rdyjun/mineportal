package com.mineportal.demo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;

/** In-memory stand-in for Mojang's join/hasJoined pair. Nothing is sent over HTTP. */
class RecordingSessionService extends SessionService {
  private final ConcurrentMap<String, GameProfile> profiles = new ConcurrentHashMap<>();
  private final AtomicInteger joins = new AtomicInteger();
  private final AtomicInteger lookups = new AtomicInteger();

  @Override
  public void joinServer(GameProfile profile, String authenticationToken, String serverId) {
    joins.incrementAndGet();
    if (profile != null && profile.getName() != null) {
      profiles.put(profile.getName(), profile);
    }
  }

  @Override
  public GameProfile getProfileByServer(String name, String serverId) {
    lookups.incrementAndGet();
    return profiles.get(name);
  }

  int joins() {
    return joins.get();
  }

  int lookups() {
    return lookups.get();
  }
}
