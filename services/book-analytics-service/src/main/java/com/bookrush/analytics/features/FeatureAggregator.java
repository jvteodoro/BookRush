package com.bookrush.analytics.features;

import java.util.*;

/** Aggregates excerpt observations without confusing missing/unsupported with zero. */
public final class FeatureAggregator {
  private FeatureAggregator() {}
  public static Map<String, Double> mean(Collection<Map<String, Double>> observations) {
    var sums = new HashMap<String, double[]>();
    for (var observation : observations) for (var e : observation.entrySet()) {
      if (e.getValue() == null || e.getValue().isNaN()) continue;
      var item = sums.computeIfAbsent(e.getKey(), ignored -> new double[2]); item[0] += e.getValue(); item[1]++;
    }
    var result = new HashMap<String, Double>(); sums.forEach((k,v) -> { if (v[1] > 0) result.put(k, v[0] / v[1]); });
    return Map.copyOf(result);
  }
}
