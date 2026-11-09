package com.agentshelf;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** Waits for background work in tests: polls, never a fixed sleep, and gives up after a limit. */
public final class Eventually {

  private Eventually() {}

  public static <T> T until(
      Supplier<T> value, java.util.function.Predicate<T> done, Duration limit) {
    Instant deadline = Instant.now().plus(limit);
    T last = value.get();
    while (!done.test(last)) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("Not done after " + limit + "; last value: " + last);
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      last = value.get();
    }
    return last;
  }
}
