package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.retry.*;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  @Test void authorizationAndMalformedInputsDoNotRetry() {
    var policy = new RetryPolicy(RandomGenerator.getDefault());
    assertFalse(policy.next(FailureClass.PAUSED_AUTHORIZATION, 1, now, null).retry());
    assertFalse(policy.next(FailureClass.QUARANTINED_INPUT, 1, now, null).retry());
  }

  @Test void retryAfterCanExceedJitterCapAndAttemptBudgetIsFive() {
    var policy = new RetryPolicy(new FixedRandom());
    var decision = policy.next(FailureClass.RETRYABLE_IO, 1, now, Duration.ofMinutes(6));
    assertEquals(now.plus(Duration.ofMinutes(6)), decision.nextAttemptAt());
    assertFalse(policy.next(FailureClass.RETRYABLE_IO, 5, now, null).retry());
  }

  private static final class FixedRandom implements RandomGenerator {
    public long nextLong(long bound) { return 0; }
    public long nextLong() { return 0; }
  }
}
