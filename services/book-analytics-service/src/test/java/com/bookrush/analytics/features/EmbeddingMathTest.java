package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class EmbeddingMathTest {
  @Test
  void weightedAggregationIsNormalized() {
    var v =
        EmbeddingMath.weightedMean(List.of(new float[] {1, 0}, new float[] {0, 1}), List.of(1, 3));
    assertEquals(1d, Math.sqrt(v[0] * v[0] + v[1] * v[1]), 1e-6);
    assertTrue(v[1] > v[0]);
  }

  @Test
  void rawDoubleCosinePreservesSignedSimilarityWithoutProbabilityConversion() {
    assertEquals(1d, EmbeddingMath.cosine(List.of(1d, 0d), List.of(1d, 0d)), 1e-9);
    assertEquals(-1d, EmbeddingMath.cosine(List.of(1d, 0d), List.of(-1d, 0d)), 1e-9);
    assertEquals(0d, EmbeddingMath.cosine(List.of(1d, 0d), List.of(0d, 1d)), 1e-9);
    assertTrue(EmbeddingMath.cosine(List.of(0.6d, 0.8d), List.of(0.6d, 0.8d)) <= 1d);
  }
}
