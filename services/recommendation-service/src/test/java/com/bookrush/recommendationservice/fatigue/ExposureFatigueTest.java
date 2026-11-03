package com.bookrush.recommendationservice.fatigue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExposureFatigueTest {
  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
  private static final ExposureFatigue FATIGUE = new ExposureFatigue(
      new ExposureFatigue.Config("fatigue-v1", Duration.ofDays(1), .2, .8));

  @Test
  void recentViewableExposureIsPenalizedAndPenaltyDecays() {
    var recent = new ExposureFatigue.Signal(2, NOW);
    var old = new ExposureFatigue.Signal(2, NOW.minus(Duration.ofDays(1)));
    assertEquals(.4, FATIGUE.penalty(recent, NOW), .0001);
    assertEquals(.2, FATIGUE.penalty(old, NOW), .0001);
    assertTrue(FATIGUE.multiplier(recent, NOW) < FATIGUE.multiplier(old, NOW));
  }

  @Test
  void nonViewableOrMissingHistoryHasNoPenalty() {
    assertEquals(0, FATIGUE.penalty(new ExposureFatigue.Signal(0, NOW), NOW));
    assertEquals(0, FATIGUE.penalty(null, NOW));
  }
}
