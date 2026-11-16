package com.mineportal.update;

import java.time.Duration;

/** How often an automatic check runs. Manual checks are not limited by this. */
public final class UpdateSchedule {

  public static final Duration INITIAL_DELAY = Duration.ofSeconds(4);
  public static final Duration INTERVAL = Duration.ofHours(6);

  private UpdateSchedule() {}
}
