package com.bookrush.analytics.excerpts;

import java.util.*;

/** Transparent weighted baseline; score is a ranking signal, never a probability. */
public final class ExcerptRanker {
  private ExcerptRanker() {}
  public static Ranked rank(Map<String,Double> features, Weights weights) {
    double score=0; var contributions=new LinkedHashMap<String,Double>();
    for (var e:weights.values().entrySet()) { double value=features.getOrDefault(e.getKey(),0d); double contribution=value*e.getValue(); contributions.put(e.getKey(),contribution); score+=contribution; }
    return new Ranked(Math.max(0,Math.min(1,score)), weights.version(), Map.copyOf(contributions));
  }
  public record Weights(String version, Map<String,Double> values) { public Weights { version=Objects.requireNonNull(version); values=Map.copyOf(values); } }
  public record Ranked(double score,String version,Map<String,Double> contributions) {}
}
