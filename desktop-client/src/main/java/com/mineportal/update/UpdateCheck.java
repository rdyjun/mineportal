package com.mineportal.update;

/** Result of one update check. Failures carry no offer and are safe to ignore. */
public final class UpdateCheck {

  public enum Status {
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    CHECK_FAILED
  }

  private final Status status;
  private final UpdateOffer offer;

  private UpdateCheck(Status status, UpdateOffer offer) {
    this.status = status;
    this.offer = offer;
  }

  public static UpdateCheck available(UpdateOffer offer) {
    return new UpdateCheck(Status.UPDATE_AVAILABLE, offer);
  }

  public static UpdateCheck upToDate() {
    return new UpdateCheck(Status.UP_TO_DATE, null);
  }

  public static UpdateCheck failed() {
    return new UpdateCheck(Status.CHECK_FAILED, null);
  }

  public Status status() {
    return status;
  }

  public UpdateOffer offer() {
    return offer;
  }
}
