package com.bookrush.recommendationservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.bookrush.recommendationservice.ranking.AnalyticsScoreClient.BookScore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationProvenanceTest {
  private static Map<String, Object> book(String id) {
    return Map.of("id", id, "canonicalTitle", "Book " + id);
  }

  @Test
  void hashIsStableRegardlessOfCandidateMapOrder() {
    var first = List.of(book("b"), book("a"));
    var second = List.of(book("a"), book("b"));
    var scores = Map.of("a", new BookScore(.2, "ranker-v1"),
        "b", new BookScore(.8, "ranker-v1"));

    assertEquals(RecommendationProvenance.candidateSetHash(first, scores, "model-v1"),
        RecommendationProvenance.candidateSetHash(second, scores, "model-v1"));
  }

  @Test
  void hashChangesWhenScoreOrModelChanges() {
    var books = List.of(book("a"));
    var original = Map.of("a", new BookScore(.2, "ranker-v1"));
    var changedScore = Map.of("a", new BookScore(.3, "ranker-v1"));
    var changedModel = RecommendationProvenance.candidateSetHash(books, original, "model-v2");

    assertNotEquals(RecommendationProvenance.candidateSetHash(books, original, "model-v1"),
        RecommendationProvenance.candidateSetHash(books, changedScore, "model-v1"));
    assertNotEquals(RecommendationProvenance.candidateSetHash(books, original, "model-v1"), changedModel);
  }
}
