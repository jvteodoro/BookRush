package com.bookrush.analytics.excerpts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalizes optional H/C/A/I/Q dimensions without inventing values.
 *
 * <p>Dimensions are model inference or human labels, not structural features. Missing or
 * non-valid inputs remain explicitly unavailable so a rank cannot silently become a label.
 */
public final class CandidateDimensionSnapshot {
  private static final Map<String, String> DIMENSIONS =
      Map.of(
          "hook", "narrative.hook",
          "curiosity", "narrative.curiosity",
          "autonomy", "narrative.autonomy",
          "intensity", "narrative.intensity",
          "presentation_quality", "narrative.presentation_quality");

  private CandidateDimensionSnapshot() {}

  public static Map<String, Dimension> from(
      Map<String, Double> features, Map<String, String> statuses) {
    var result = new LinkedHashMap<String, Dimension>();
    DIMENSIONS.forEach(
        (dimension, feature) -> {
          String status = statuses.getOrDefault(feature, "UNAVAILABLE");
          Double value = "VALID".equals(status) ? features.get(feature) : null;
          result.put(dimension, new Dimension(value, status, feature));
        });
    return Map.copyOf(result);
  }

  public record Dimension(Double value, String status, String sourceFeature) {}
}
