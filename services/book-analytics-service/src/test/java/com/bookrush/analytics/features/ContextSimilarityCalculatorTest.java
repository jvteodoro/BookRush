package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContextSimilarityCalculatorTest {
  @Test
  void reportsCosinesAsObservations() {
    var result =
        ContextSimilarityCalculator.calculate(
            new float[] {1, 0}, new float[] {1, 0}, new float[] {0, 1});
    assertEquals("VALID", result.status());
    assertEquals(1d, result.values().get("semantic.context.document_cosine"));
    assertEquals(0d, result.values().get("semantic.context.chapter_cosine"));
  }

  @Test
  void reportsPreviousNextContextAndExplicitShifts() {
    var result = ContextSimilarityCalculator.calculate(
        new float[] {1, 0}, new float[] {1, 0}, new float[] {0, 1}, null, null);
    assertEquals("VALID", result.status());
    assertEquals(1d, result.values().get("semantic.context.previous_cosine"));
    assertEquals(0d, result.values().get("semantic.shift.in"));
    assertEquals(0d, result.values().get("semantic.context.next_cosine"));
    assertEquals(1d, result.values().get("semantic.shift.out"));
    assertEquals(1d, result.values().get("semantic.shift.max"));
  }
}
