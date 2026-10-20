package com.bookrush.analytics.excerpts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Versioned, explicitly unvalidated configuration for the transparent baseline ranker. */
public record HeuristicRankerConfig(
    String code, String version, String state, Map<String, Double> weights) {

  public HeuristicRankerConfig {
    code = Objects.requireNonNull(code);
    version = Objects.requireNonNull(version);
    state = Objects.requireNonNull(state);
    weights = Map.copyOf(weights);
    if (!"EXCERPT_HEURISTIC_RANKER".equals(code))
      throw new IllegalArgumentException("unexpected ranker code");
    if (!"UNVALIDATED".equals(state))
      throw new IllegalArgumentException("bootstrap ranker must remain UNVALIDATED");
    if (weights.isEmpty() || weights.values().stream().anyMatch(v -> v == null || !Double.isFinite(v) || v < 0))
      throw new IllegalArgumentException("ranker weights must be finite and non-negative");
  }

  public static HeuristicRankerConfig load(InputStream input) {
    try {
      var node = new ObjectMapper().readTree(input);
      var weights = new LinkedHashMap<String, Double>();
      node.path("weights").fields().forEachRemaining(entry -> weights.put(entry.getKey(), entry.getValue().doubleValue()));
      return new HeuristicRankerConfig(
          node.path("code").asText(), node.path("version").asText(), node.path("state").asText(), weights);
    } catch (IOException e) {
      throw new IllegalStateException("unable to load ranker configuration", e);
    }
  }

  public ExcerptRanker.Weights asWeights() {
    return new ExcerptRanker.Weights(version, weights);
  }
}
