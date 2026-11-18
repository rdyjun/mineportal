package com.mineportal.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatGuardTest {
  @Test
  void lengthDoesNotConsumeToken() {
    ChatGuard guard = guard(4, 1, 30, 10, clock(0));

    assertEquals(ChatGuard.Decision.TOO_LONG, guard.admit("12345"));
    assertEquals(ChatGuard.Decision.ACCEPT, guard.admit("1234"));
    assertEquals(ChatGuard.Decision.TOO_FAST, guard.admit("ok"));
  }

  @Test
  void secondOversizeIsSilent() {
    ChatGuard guard = guard(4, 5, 30, 10, clock(0));

    assertEquals(ChatGuard.Decision.TOO_LONG, guard.admit("12345"));
    assertEquals(ChatGuard.Decision.SILENT, guard.admit("123456"));
  }

  @Test
  void rateThenSilent() {
    ChatGuard guard = guard(16, 1, 30, 10, clock(0));

    assertEquals(ChatGuard.Decision.ACCEPT, guard.admit("a"));
    assertEquals(ChatGuard.Decision.TOO_FAST, guard.admit("b"));
    assertEquals(ChatGuard.Decision.SILENT, guard.admit("c"));
  }

  @Test
  void acceptUpdatesIdle() {
    long[] now = {1_000};
    ChatGuard guard = guard(16, 5, 30, 1, () -> now[0]);

    assertFalse(guard.idle());
    now[0] += 1_000;
    assertTrue(guard.idle());
    assertEquals(ChatGuard.Decision.ACCEPT, guard.admit("hi"));
    assertFalse(guard.idle());
  }

  @Test
  void blankIsBlank() {
    ChatGuard guard = guard(16, 5, 30, 10, clock(0));

    assertEquals(ChatGuard.Decision.BLANK, guard.admit(""));
    assertEquals(ChatGuard.Decision.BLANK, guard.admit("   "));
    assertEquals(ChatGuard.Decision.BLANK, guard.admit(null));
  }

  private static ChatGuard guard(
      int chars,
      int limit,
      int windowSeconds,
      int idleSeconds,
      java.util.function.LongSupplier clock) {
    return new ChatGuard(
        new DemoSettings("127.0.0.1", 16, chars, limit, windowSeconds, idleSeconds, false, 60),
        clock);
  }

  private static java.util.function.LongSupplier clock(long time) {
    return () -> time;
  }
}
