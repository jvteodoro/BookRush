package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NliScoreTest {
  @Test void derivesSupportAndConfidenceWithoutCallingThemProbability() {
    var score = new NliScore(.6, .2, .2);
    assertEquals(.75, score.support(), 1e-9);
    assertEquals(.8, score.confidence(), 1e-9);
  }

  @Test void guardsZeroDenominatorAndInvalidValues() {
    assertEquals(0, new NliScore(0, 1, 0).support());
    assertThrows(IllegalArgumentException.class, () -> new NliScore(-.1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new NliScore(Double.NaN, 0, 0));
  }
}
