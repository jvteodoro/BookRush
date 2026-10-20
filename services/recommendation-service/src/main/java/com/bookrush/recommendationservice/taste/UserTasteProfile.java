package com.bookrush.recommendationservice.taste;

import java.time.Instant;
import java.util.List;

/** Immutable, versioned representation of observed user taste. */
public record UserTasteProfile(
    Status status,
    List<Double> vector,
    String profileVersion,
    String configurationHash,
    int effectiveSignalCount,
    double coverage,
    double confidence,
    Instant generatedAt) {
  public enum Status { EMPTY, READY }

  public UserTasteProfile {
    vector = vector == null ? null : List.copyOf(vector);
    if (coverage < 0 || coverage > 1 || confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("coverage and confidence must be between 0 and 1");
    }
    if (status == Status.EMPTY && vector != null) {
      throw new IllegalArgumentException("empty profile cannot carry a vector");
    }
  }
}
