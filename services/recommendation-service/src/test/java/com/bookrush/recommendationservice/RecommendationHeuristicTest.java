package com.bookrush.recommendationservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;
import com.bookrush.recommendationservice.fatigue.ExposureFatigue;
import com.bookrush.recommendationservice.ranking.AnalyticsScoreClient.BookScore;
import org.junit.jupiter.api.Test;

class RecommendationHeuristicTest {
  private static Map<String, Object> book(String id, String language) {
    return Map.of("id", id, "originalLanguage", language);
  }

  @Test
  void strategyTransitionsAreVersionedAndDeterministic() {
    assertEquals(RecommendationHeuristic.Strategy.COLD_START,
        RecommendationHeuristic.strategyFor(0));
    assertEquals(RecommendationHeuristic.Strategy.DIVERSE,
        RecommendationHeuristic.strategyFor(1));
    assertEquals(RecommendationHeuristic.Strategy.LUCKY,
        RecommendationHeuristic.strategyFor(7));
    assertEquals(RecommendationHeuristic.strategyFor(14),
        RecommendationHeuristic.strategyFor(7));
  }

  @Test
  void rankingIsStableForSameSubjectAndChangesOnlyForLuckyExploration() {
    var books = List.of(book("a", "en"), book("b", "pt"), book("c", "en"));
    var first = RecommendationHeuristic.rank(books, "reader-1", 3,
        RecommendationHeuristic.Strategy.DIVERSE);
    var second = RecommendationHeuristic.rank(books, "reader-1", 3,
        RecommendationHeuristic.Strategy.DIVERSE);
    assertEquals(first, second);
    assertNotEquals(first, RecommendationHeuristic.rank(books, "reader-1", 3,
        RecommendationHeuristic.Strategy.LUCKY));
  }

  @Test
  void rankingPrefersDifferentLanguagesBeforeAllowingDuplicates() {
    var books = List.of(book("a", "en"), book("b", "en"), book("c", "pt"));
    var selected = RecommendationHeuristic.rank(books, "reader-1", 2,
        RecommendationHeuristic.Strategy.DIVERSE);
    long distinctLanguages = selected.stream()
        .map(book -> ((Map<?, ?>) book).get("originalLanguage"))
        .distinct().count();
    assertEquals(2, distinctLanguages);
  }

  @Test
  void analyticsScoreOrdersCandidatesBeforeDeterministicFallback() {
    var books = List.of(book("a", "en"), book("b", "pt"), book("c", "en"));
    var ranked = RecommendationHeuristic.rank(books, "reader-1", 3,
        RecommendationHeuristic.Strategy.DIVERSE,
        Map.of("a", new BookScore(0.2, "EXCERPT_HEURISTIC_RANKER_V1"),
            "b", new BookScore(0.9, "EXCERPT_HEURISTIC_RANKER_V1")));
    assertEquals("b", ((Map<?, ?>) ranked.get(0)).get("id"));
    assertEquals("a", ((Map<?, ?>) ranked.get(1)).get("id"));
  }

  @Test
  void recentViewableExposureLowersEffectiveAnalyticsScore() {
    var books = List.of(book("a", "en"), book("b", "pt"));
    var now = Instant.parse("2026-10-20T00:00:00Z");
    var ranked = RecommendationHeuristic.rank(books, "reader-1", 2,
        RecommendationHeuristic.Strategy.DIVERSE,
        Map.of("a", new BookScore(.9, "v1"), "b", new BookScore(.8, "v1")),
        Map.of("a", new ExposureFatigue.Signal(2, now)), now,
        new ExposureFatigue(new ExposureFatigue.Config("v1", Duration.ofDays(1), .2, .8)));
    assertEquals("b", ((Map<?, ?>) ranked.get(0)).get("id"));
  }
}
