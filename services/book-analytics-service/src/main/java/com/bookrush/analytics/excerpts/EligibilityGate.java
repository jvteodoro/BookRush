package com.bookrush.analytics.excerpts;

import java.util.*;

/** Hard technical gates applied before product ranking or MMR. */
public final class EligibilityGate {
  private EligibilityGate() {}
  public static Decision evaluate(Map<String, Double> features, Map<String, String> statuses, Bounds bounds) {
    for (var entry : statuses.entrySet()) if (!"VALID".equals(entry.getValue())) return new Decision(false, "INVALID_FEATURE:" + entry.getKey());
    int words=features.getOrDefault("word_count",0d).intValue();
    if (words < bounds.minWords()) return new Decision(false,"TOO_SHORT");
    if (words > bounds.maxWords()) return new Decision(false,"TOO_LONG");
    double autonomy=features.getOrDefault("narrative.autonomy", Double.NaN);
    if (!Double.isNaN(bounds.minAutonomy()) && (Double.isNaN(autonomy) || autonomy < bounds.minAutonomy())) return new Decision(false,"AUTONOMY_BELOW_MINIMUM");
    return new Decision(true, null);
  }
  public record Bounds(int minWords, int maxWords, double minAutonomy) {
    public Bounds { if (minWords<0 || maxWords<minWords) throw new IllegalArgumentException("invalid bounds"); }
  }
  public record Decision(boolean eligible, String exclusionReason) {}
}
