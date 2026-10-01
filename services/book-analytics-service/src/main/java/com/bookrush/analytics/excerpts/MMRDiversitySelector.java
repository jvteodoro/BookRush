package com.bookrush.analytics.excerpts;

import java.util.*;

/**
 * Deterministic maximal marginal relevance selector. Similarity is cosine,
 * while the returned score is only a selection score and never a probability.
 */
public final class MMRDiversitySelector {
  private MMRDiversitySelector() {}

  public static List<Candidate> select(List<Candidate> candidates, int limit,
                                       double relevanceWeight, double diversityWeight) {
    if (limit <= 0 || candidates.isEmpty()) return List.of();
    var remaining = new ArrayList<>(candidates);
    var selected = new ArrayList<Candidate>(Math.min(limit, candidates.size()));
    while (!remaining.isEmpty() && selected.size() < limit) {
      Candidate best = null; double bestScore = -Double.MAX_VALUE;
      for (var candidate : remaining) {
        double maxSimilarity = selected.stream()
            .mapToDouble(existing -> cosine(candidate.embedding(), existing.embedding()))
            .max().orElse(0d);
        double score = relevanceWeight * candidate.relevance() - diversityWeight * maxSimilarity;
        if (best == null || score > bestScore || (Double.compare(score, bestScore) == 0
            && candidate.id().compareTo(best.id()) < 0)) {
          best = candidate; bestScore = score;
        }
      }
      selected.add(best.withSelectionScore(bestScore));
      remaining.remove(best);
    }
    return List.copyOf(selected);
  }

  public static double cosine(float[] a, float[] b) {
    if (a == null || b == null || a.length == 0 || a.length != b.length) return 0d;
    double dot = 0, an = 0, bn = 0;
    for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; an += a[i] * a[i]; bn += b[i] * b[i]; }
    return an == 0 || bn == 0 ? 0d : dot / Math.sqrt(an * bn);
  }

  public record Candidate(String id, double relevance, float[] embedding, double selectionScore) {
    public Candidate { Objects.requireNonNull(id); embedding = embedding == null ? null : embedding.clone(); }
    public Candidate(String id, double relevance, float[] embedding) { this(id, relevance, embedding, 0d); }
    public Candidate withSelectionScore(double score) { return new Candidate(id, relevance, embedding, score); }
    @Override public float[] embedding() { return embedding == null ? null : embedding.clone(); }
  }
}
