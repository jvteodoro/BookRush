package com.bookrush.analytics.features;

import java.util.Map;

/**
 * Explainable spoiler components only. This is not a calibrated probability and must not be
 * exposed as spoiler_probability without a reviewed labeled dataset.
 */
public final class SpoilerHeuristic {
  public static final String VERSION = "spoiler-components-v1";

  private SpoilerHeuristic() {}

  public static Map<String, Double> components(
      double bookPosition, double resolutionSupport, double revelationSupport) {
    return Map.of(
        "position", clamp(bookPosition),
        "resolution", clamp(resolutionSupport),
        "revelation", clamp(revelationSupport));
  }

  private static double clamp(double value) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite spoiler component");
    return Math.max(0d, Math.min(1d, value));
  }
}
