package com.bookrush.ingestion.retry;

import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;

/** Calculates a persisted next-attempt time; it never sleeps inside a worker. */
public final class RetryPolicy {
  private static final Duration BASE = Duration.ofSeconds(2);
  private static final Duration CAP = Duration.ofMinutes(5);
  private final RandomGenerator random;

  public RetryPolicy(RandomGenerator random) {
    this.random = random;
  }

  public Decision next(FailureClass failure, int attemptNumber, Instant now, Duration retryAfter) {
    int maxAttempts = failure == FailureClass.TRANSIENT_DATABASE ? 3 : 5;
    if (failure != FailureClass.RETRYABLE_IO && failure != FailureClass.TRANSIENT_DATABASE) {
      return new Decision(false, now, failure.name());
    }
    if (attemptNumber >= maxAttempts) return new Decision(false, now, "ATTEMPT_BUDGET_EXHAUSTED");
    long exponential = BASE.toMillis() * (1L << Math.min(20, Math.max(0, attemptNumber - 1)));
    long bounded = Math.min(CAP.toMillis(), exponential);
    long jitter = bounded == 0 ? 0 : random.nextLong(bounded + 1);
    Duration delay = Duration.ofMillis(jitter);
    if (retryAfter != null && retryAfter.compareTo(delay) > 0) delay = retryAfter;
    return new Decision(true, now.plus(delay), failure.name());
  }

  public record Decision(boolean retry, Instant nextAttemptAt, String reason) {}
}
