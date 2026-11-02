package com.mineportal.server.account;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class DesktopLoginAttemptsTest {
  @Test
  void onlyTheInitiatingAppCanClaimAndOnlyOnce() {
    var attempts = new DesktopLoginAttempts();
    var started = attempts.start("app");
    String confirmation = attempts.prepare(started.id(), "browser");
    attempts.authorize(started.id(), "browser", confirmation);
    assertNull(attempts.claim(started.id(), "app", started.proof()));
    attempts.complete(started.id(), "rotated-authenticated-session");
    assertThrows(
        IllegalArgumentException.class,
        () -> attempts.claim(started.id(), "other-app", started.proof()));
    assertThrows(
        IllegalArgumentException.class, () -> attempts.claim(started.id(), "app", "wrong-proof"));
    assertEquals(
        "rotated-authenticated-session", attempts.claim(started.id(), "app", started.proof()));
    assertThrows(
        IllegalArgumentException.class, () -> attempts.claim(started.id(), "app", started.proof()));
  }

  @Test
  void browserMustExplicitlyConfirmWithItsOwnNonceAndSession() {
    var attempts = new DesktopLoginAttempts();
    var started = attempts.start("app");
    assertThrows(IllegalArgumentException.class, () -> attempts.complete(started.id(), "sid"));
    String confirmation = attempts.prepare(started.id(), "browser");
    assertThrows(
        IllegalArgumentException.class,
        () -> attempts.authorize(started.id(), "other-browser", confirmation));
    assertThrows(
        IllegalArgumentException.class,
        () -> attempts.authorize(started.id(), "browser", "wrong-confirmation"));
    attempts.authorize(started.id(), "browser", confirmation);
    assertThrows(
        IllegalArgumentException.class,
        () -> attempts.authorize(started.id(), "browser", confirmation));
    attempts.complete(started.id(), "sid");
    assertThrows(
        IllegalArgumentException.class, () -> attempts.complete(started.id(), "other-sid"));
  }

  @Test
  void newAttemptInvalidatesOlderAttemptForTheSameApp() {
    var attempts = new DesktopLoginAttempts();
    var old = attempts.start("app");
    attempts.start("app");
    assertThrows(IllegalArgumentException.class, () -> attempts.prepare(old.id(), "browser"));
  }

  @Test
  void expiredAttemptsCannotAuthorizeOrClaim() {
    Clock clock = org.mockito.Mockito.mock(Clock.class);
    org.mockito.Mockito.when(clock.millis()).thenReturn(0L);
    var attempts = new DesktopLoginAttempts(clock);
    var started = attempts.start("app");
    String confirmation = attempts.prepare(started.id(), "browser");
    org.mockito.Mockito.when(clock.millis()).thenReturn(10 * 60 * 1000L);
    assertThrows(
        IllegalArgumentException.class,
        () -> attempts.authorize(started.id(), "browser", confirmation));
    assertThrows(
        IllegalArgumentException.class, () -> attempts.claim(started.id(), "app", started.proof()));
  }
}
