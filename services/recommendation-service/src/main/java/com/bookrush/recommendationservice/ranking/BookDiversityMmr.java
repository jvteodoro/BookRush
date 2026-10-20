package com.bookrush.recommendationservice.ranking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Book-level MMR; deliberately separate from excerpt-level analytics MMR. */
public final class BookDiversityMmr {
  private final Config config;

  public BookDiversityMmr(Config config) { this.config = config; }

  public List<Selection> select(List<PersonalizedRankerV1.ScoredCandidate> ranked,
      Map<String, List<Double>> embeddings, int limit) {
    if (ranked == null || limit < 1) return List.of();
    var remaining = new ArrayList<>(ranked);
    var selected = new ArrayList<Selection>();
    while (!remaining.isEmpty() && selected.size() < limit) {
      PersonalizedRankerV1.ScoredCandidate best = null;
      double bestMmr = Double.NEGATIVE_INFINITY;
      for (var candidate : remaining) {
        double redundancy = maxSimilarity(candidate.candidate().id(), selected, embeddings);
        double mmr = config.lambda() * candidate.score() - (1 - config.lambda()) * redundancy;
        if (best == null || mmr > bestMmr || (mmr == bestMmr
            && candidate.candidate().id().compareTo(best.candidate().id()) < 0)) {
          best = candidate;
          bestMmr = mmr;
        }
      }
      remaining.remove(best);
      double redundancy = maxSimilarity(best.candidate().id(), selected, embeddings);
      selected.add(new Selection(best, bestMmr, redundancy,
          embeddings.containsKey(best.candidate().id()) ? "embedding" : "missing-embedding-fallback"));
    }
    return List.copyOf(selected);
  }

  private double maxSimilarity(String id, List<Selection> selected, Map<String, List<Double>> embeddings) {
    List<Double> vector = embeddings == null ? null : embeddings.get(id);
    if (vector == null) return 0;
    return selected.stream().map(selection -> embeddings.get(selection.candidate().candidate().id()))
        .mapToDouble(other -> cosine(vector, other)).max().orElse(0);
  }

  static double cosine(List<Double> left, List<Double> right) {
    if (left == null || right == null || left.isEmpty() || left.size() != right.size()) return 0;
    double dot = 0, leftNorm = 0, rightNorm = 0;
    for (int i = 0; i < left.size(); i++) {
      if (left.get(i) == null || right.get(i) == null) return 0;
      double a = left.get(i), b = right.get(i);
      if (!Double.isFinite(a) || !Double.isFinite(b)) return 0;
      dot += a * b;
      leftNorm += a * a;
      rightNorm += b * b;
    }
    return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
  }

  public record Selection(PersonalizedRankerV1.ScoredCandidate candidate, double mmrScore,
      double maxSimilarity, String rationale) { }

  public record Config(String version, double lambda) {
    public Config {
      if (version == null || version.isBlank() || lambda < 0 || lambda > 1) {
        throw new IllegalArgumentException("lambda must be between 0 and 1");
      }
    }
    public String hash() {
      try {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((version + "|" + lambda).getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
        throw new IllegalStateException("SHA-256 is required for MMR configuration", exception);
      }
    }
  }
}
