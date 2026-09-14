package com.bookrush.analytics.features;

import java.time.Instant;
import java.util.*;

/** Converts analyzer output into typed, versioned observations ready for persistence. */
public final class ExcerptFeatureObservationFactory {
  private ExcerptFeatureObservationFactory() {}
  public static List<Observation> from(UUID runId, UUID excerptId, UUID analyzerId, Map<String,Double> values, Instant computedAt) {
    return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
        .map(e -> new Observation(runId, excerptId, analyzerId, e.getKey(), e.getValue(), computedAt)).toList();
  }
  public record Observation(UUID analysisRunId, UUID excerptId, UUID analyzerId, String featureCode, Double numericValue, Instant computedAt) {}
}
