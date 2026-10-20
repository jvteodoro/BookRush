package com.bookrush.recommendationservice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.bookrush.recommendationservice.ranking.AnalyticsScoreClient.BookScore;
import com.bookrush.recommendationservice.fatigue.ExposureFatigue;
import java.time.Instant;

/** Deterministic heuristic-v1 policy kept independent from HTTP and JDBC. */
public final class RecommendationHeuristic {
  public enum Strategy { COLD_START, DIVERSE, LUCKY }

  private RecommendationHeuristic() {}

  public static Strategy strategyFor(long previousImpressions) {
    if (previousImpressions <= 0) return Strategy.COLD_START;
    return previousImpressions % 7 == 0 ? Strategy.LUCKY : Strategy.DIVERSE;
  }

  public static List<?> rank(List<?> books, String subject, int limit, Strategy strategy) {
    return rank(books, subject, limit, strategy, Map.of());
  }

  public static List<?> rank(List<?> books, String subject, int limit, Strategy strategy,
      Map<String, BookScore> analyticsScores) {
    return rank(books, subject, limit, strategy, analyticsScores, Map.of(), Instant.now(),
        new com.bookrush.recommendationservice.fatigue.ExposureFatigue(
            new com.bookrush.recommendationservice.fatigue.ExposureFatigue.Config(
                "fatigue-v1", java.time.Duration.ofDays(7), .15, .8)));
  }

  public static List<?> rank(List<?> books, String subject, int limit, Strategy strategy,
      Map<String, BookScore> analyticsScores, Map<String, ExposureFatigue.Signal> fatigue,
      Instant now, ExposureFatigue fatiguePolicy) {
    int boundedLimit = Math.max(1, limit);
    var candidates = new ArrayList<>(books);
    candidates.sort((left, right) -> {
      int analyticsOrder = Double.compare(effectiveScore(right, analyticsScores, fatigue, now, fatiguePolicy),
          effectiveScore(left, analyticsScores, fatigue, now, fatiguePolicy));
      if (analyticsOrder != 0) return analyticsOrder;
      return Long.compare(score(left, subject, strategy), score(right, subject, strategy));
    });
    var selected = new ArrayList<Object>();
    Set<String> seenKeys = new HashSet<>();
    for (Object candidate : candidates) {
      String key = diversityKey(candidate);
      if (seenKeys.add(key)) selected.add(candidate);
      if (selected.size() == boundedLimit) break;
    }
    if (selected.size() < boundedLimit) {
      for (Object candidate : candidates) {
        if (selected.contains(candidate)) continue;
        selected.add(candidate);
        if (selected.size() == boundedLimit) break;
      }
    }
    return selected;
  }

  private static double effectiveScore(Object book, Map<String, BookScore> analyticsScores,
      Map<String, ExposureFatigue.Signal> fatigue, Instant now, ExposureFatigue fatiguePolicy) {
    Object id = book instanceof Map<?, ?> map ? map.get("id") : book;
    var signal = fatigue.get(String.valueOf(id));
    double multiplier = signal == null ? 1 : fatiguePolicy.multiplier(signal, now);
    return analyticsScore(book, analyticsScores) * multiplier;
  }

  private static double analyticsScore(Object book, Map<String, BookScore> analyticsScores) {
    Object id = book instanceof Map<?, ?> map ? map.get("id") : book;
    BookScore score = analyticsScores.get(String.valueOf(id));
    return score == null ? Double.NEGATIVE_INFINITY : score.score();
  }

  private static long score(Object book, String subject, Strategy strategy) {
    Object id = book instanceof Map<?, ?> map ? map.get("id") : book;
    String salt = strategy == Strategy.LUCKY ? ":lucky" : "";
    long hash = (subject + ":" + id + salt).hashCode();
    return strategy == Strategy.COLD_START ? Math.floorMod(hash, Integer.MAX_VALUE) : hash;
  }

  private static String diversityKey(Object book) {
    if (!(book instanceof Map<?, ?> map)) return String.valueOf(book);
    Object language = map.get("originalLanguage");
    if (language == null) language = map.get("language");
    return language == null ? String.valueOf(map.get("id")) : String.valueOf(language);
  }
}
