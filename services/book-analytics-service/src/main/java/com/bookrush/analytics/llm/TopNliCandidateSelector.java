package com.bookrush.analytics.llm;

import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import java.util.*;

/** Selects a bounded, reproducible and diverse subset before expensive NLI inference. */
public final class TopNliCandidateSelector {
  private TopNliCandidateSelector() {}

  public static List<Selection> select(
      List<ExcerptCandidateGenerator.V2Candidate> candidates,
      List<UUID> excerptIds,
      Map<UUID, List<Double>> embeddings,
      int requestedLimit) {
    if (candidates.size() != excerptIds.size())
      throw new IllegalArgumentException("candidates and excerptIds must have the same size");
    if (requestedLimit < 0) throw new IllegalArgumentException("limit must be non-negative");
    int limit = Math.min(requestedLimit, 50);
    if (limit == 0 || candidates.isEmpty()) return List.of();

    var remaining = new ArrayList<Ranked>();
    for (int i = 0; i < candidates.size(); i++) {
      var candidate = candidates.get(i);
      var id = excerptIds.get(i);
      var vector = embeddings.get(id);
      remaining.add(new Ranked(id, candidate, vector, relevance(candidate, vector)));
    }
    remaining.sort(Ranked.ORDER);

    var selected = new ArrayList<Ranked>();
    while (selected.size() < limit && !remaining.isEmpty()) {
      Ranked best = null;
      double bestScore = Double.NEGATIVE_INFINITY;
      for (var item : remaining) {
        double diversity = selected.isEmpty() ? 1d : minDistance(item, selected);
        // MMR keeps high-quality candidates while avoiding one chapter/near-duplicate cluster.
        double score = 0.70d * item.relevance() + 0.30d * diversity;
        if (best == null || score > bestScore || (score == bestScore && Ranked.ORDER.compare(item, best) < 0)) {
          best = item;
          bestScore = score;
        }
      }
      selected.add(best);
      remaining.remove(best);
    }
    return selected.stream().map(r -> new Selection(r.id(), r.candidate(), r.relevance())).toList();
  }

  private static double relevance(ExcerptCandidateGenerator.V2Candidate candidate, List<Double> vector) {
    String text = candidate.text().toLowerCase(Locale.ROOT);
    long words = text.codePoints().filter(Character::isLetterOrDigit).count();
    long unique = Arrays.stream(text.split("[^\\p{L}\\p{N}]+"))
        .filter(s -> !s.isBlank()).map(String::strip).distinct().count();
    double lexical = words == 0 ? 0d : Math.min(1d, (double) unique / Math.max(1d, words / 4d));
    double structure = Math.min(1d, candidate.sentenceCount() / 8d) * 0.5d
        + Math.min(1d, candidate.wordCount() / 120d) * 0.5d;
    double bgeQuality = vector == null || vector.isEmpty() ? 0d : 1d;
    return 0.40d * lexical + 0.35d * structure + 0.25d * bgeQuality;
  }

  private static double minDistance(Ranked item, List<Ranked> selected) {
    if (item.vector() == null || item.vector().isEmpty()) return 0d;
    return selected.stream()
        .mapToDouble(previous -> 1d - cosine(item.vector(), previous.vector()))
        .min().orElse(0d);
  }

  private static double cosine(List<Double> left, List<Double> right) {
    if (right == null || left.size() != right.size() || left.isEmpty()) return 0d;
    double dot = 0d;
    for (int i = 0; i < left.size(); i++) dot += left.get(i) * right.get(i);
    return Math.max(-1d, Math.min(1d, dot));
  }

  public record Selection(UUID excerptId, ExcerptCandidateGenerator.V2Candidate candidate, double relevance) {}

  private record Ranked(UUID id, ExcerptCandidateGenerator.V2Candidate candidate, List<Double> vector, double relevance) {
    private static final Comparator<Ranked> ORDER = Comparator
        .comparingDouble(Ranked::relevance).reversed()
        .thenComparing(r -> r.candidate().chapterId(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(r -> r.candidate().textSha256());
  }
}
