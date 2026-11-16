package com.mineportal.update;

import java.util.Optional;

/** Source of the newest published release. Implementations should not throw. */
public interface ReleaseSource {

  Optional<ReleaseInfo> fetchLatest();
}
