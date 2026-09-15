package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class EmbeddingMathTest { @Test void weightedAggregationIsNormalized(){var v=EmbeddingMath.weightedMean(List.of(new float[]{1,0},new float[]{0,1}),List.of(1,3)); assertEquals(1d,Math.sqrt(v[0]*v[0]+v[1]*v[1]),1e-6); assertTrue(v[1]>v[0]);} }
