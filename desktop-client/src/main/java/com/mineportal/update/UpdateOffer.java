package com.mineportal.update;

import java.util.Objects;

/** A stable release that is newer than the running build, plus a safe place to read it. */
public final class UpdateOffer {

  private final AppVersion current;
  private final AppVersion latest;
  private final String pageUrl;
  private final String assetUrl;

  public UpdateOffer(AppVersion current, AppVersion latest, String pageUrl, String assetUrl) {
    this.current = Objects.requireNonNull(current);
    this.latest = Objects.requireNonNull(latest);
    if (pageUrl == null && assetUrl == null) {
      throw new IllegalArgumentException("update offer needs a page or an asset url");
    }
    this.pageUrl = pageUrl;
    this.assetUrl = assetUrl;
  }

  public AppVersion current() {
    return current;
  }

  public AppVersion latest() {
    return latest;
  }

  public String pageUrl() {
    return pageUrl;
  }

  public String assetUrl() {
    return assetUrl;
  }

  public boolean directDownload() {
    return assetUrl != null;
  }

  public String openUrl() {
    return assetUrl != null ? assetUrl : pageUrl;
  }
}
