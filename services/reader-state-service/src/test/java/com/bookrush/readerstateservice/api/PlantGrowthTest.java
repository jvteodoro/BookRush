package com.bookrush.readerstateservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlantGrowthTest {
  @Test
  void readingGrowsOrRecoversButNeverExceedsTheMaximum() {
    assertEquals(new PlantGrowth.State(1, false), PlantGrowth.readingGoalReached(new PlantGrowth.State(0, false)));
    assertEquals(new PlantGrowth.State(2, false), PlantGrowth.readingGoalReached(new PlantGrowth.State(2, true)));
    assertEquals(new PlantGrowth.State(5, false), PlantGrowth.readingGoalReached(new PlantGrowth.State(5, false)));
  }

  @Test
  void missedDaysAlternateSadnessAndDowngradeUntilZero() {
    var state = new PlantGrowth.State(2, false);
    for (var expected : new PlantGrowth.State[] {new PlantGrowth.State(2, true),
        new PlantGrowth.State(1, false), new PlantGrowth.State(1, true),
        new PlantGrowth.State(0, false), new PlantGrowth.State(0, false)}) {
      state = PlantGrowth.missedDay(state);
      assertEquals(expected, state);
    }
  }

  @Test
  void stageZeroCannotBeSad() {
    assertThrows(IllegalArgumentException.class, () -> new PlantGrowth.State(0, true));
  }
}
