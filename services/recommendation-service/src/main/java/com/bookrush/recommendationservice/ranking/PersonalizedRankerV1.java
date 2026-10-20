package com.bookrush.recommendationservice.ranking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Explainable bootstrap ranker; weights are unvalidated, not probabilities. */
public final class PersonalizedRankerV1 {
  public static final String MODEL_VERSION = "PERSONALIZED_V1";
  private final Config config;

  public PersonalizedRankerV1(Config config) { this.config = Objects.requireNonNull(config); }

  public String configurationHash() { return config.hash(); }

  public List<ScoredCandidate> rank(List<Candidate> candidates, long seed) {
    return candidates == null ? List.of() : candidates.stream().filter(Objects::nonNull)
        .map(candidate -> score(candidate, seed)).sorted(Comparator
            .comparingDouble(ScoredCandidate::score).reversed()
            .thenComparingLong(ScoredCandidate::tieBreak)
            .thenComparing(candidate -> candidate.candidate().id())).toList();
  }

  public ScoredCandidate score(Candidate candidate, long seed) {
    var values = candidate.components();
    double weighted = 0;
    double totalWeight = 0;
    for (Component component : Component.values()) {
      Double value = values.value(component);
      double weight = config.weight(component);
      if (value == null) {
        if (config.missingPolicy() == MissingPolicy.EXCLUDE) {
          return new ScoredCandidate(candidate, 0, new Breakdown(0, 0, 0, 0, 0), tieBreak(candidate.id(), seed));
        }
        if (config.missingPolicy() == MissingPolicy.ZERO) value = 0d;
        else continue;
      }
      weighted += clamp(value) * weight;
      totalWeight += weight;
    }
    double normalized = totalWeight == 0 ? 0 : weighted / totalWeight;
    var breakdown = new Breakdown(value(values.affinity()), value(values.quality()),
        value(values.trend()), value(values.exploration()), value(values.fatigue()));
    return new ScoredCandidate(candidate, normalized, breakdown, tieBreak(candidate.id(), seed));
  }

  private static double value(Double value) { return value == null ? 0 : clamp(value); }
  private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

  private static long tieBreak(String id, long seed) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((seed + ":" + id).getBytes(StandardCharsets.UTF_8));
      long value = 0;
      for (int i = 0; i < 8; i++) value = (value << 8) | (digest[i] & 0xffL);
      return value;
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is required for deterministic exploration", exception);
    }
  }

  public enum Component { AFFINITY, QUALITY, TREND, EXPLORATION, FATIGUE }
  public enum MissingPolicy { RENORMALIZE, ZERO, EXCLUDE }

  public record Candidate(String id, Components components) {
    public Candidate {
      if (id == null || id.isBlank() || components == null) throw new IllegalArgumentException("candidate required");
    }
  }

  public record Components(Double affinity, Double quality, Double trend,
      Double exploration, Double fatigue) {
    public Double value(Component component) {
      return switch (component) {
        case AFFINITY -> affinity;
        case QUALITY -> quality;
        case TREND -> trend;
        case EXPLORATION -> exploration;
        case FATIGUE -> fatigue;
      };
    }
  }

  public record Breakdown(double affinity, double quality, double trend,
      double exploration, double fatigue) { }
  public record ScoredCandidate(Candidate candidate, double score, Breakdown breakdown, long tieBreak) { }

  public record Config(String version, double affinityWeight, double qualityWeight,
      double trendWeight, double explorationWeight, double fatigueWeight,
      MissingPolicy missingPolicy) {
    public Config {
      if (version == null || version.isBlank() || missingPolicy == null
          || affinityWeight < 0 || qualityWeight < 0 || trendWeight < 0
          || explorationWeight < 0 || fatigueWeight < 0
          || affinityWeight + qualityWeight + trendWeight + explorationWeight + fatigueWeight == 0) {
        throw new IllegalArgumentException("invalid personalized ranker configuration");
      }
    }
    double weight(Component component) {
      return switch (component) {
        case AFFINITY -> affinityWeight;
        case QUALITY -> qualityWeight;
        case TREND -> trendWeight;
        case EXPLORATION -> explorationWeight;
        case FATIGUE -> fatigueWeight;
      };
    }
    public String hash() { return Integer.toHexString(toString().hashCode()); }
  }
}
