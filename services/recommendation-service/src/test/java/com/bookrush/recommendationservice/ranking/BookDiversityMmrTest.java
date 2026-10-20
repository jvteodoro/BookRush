package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BookDiversityMmrTest {
  private static PersonalizedRankerV1.ScoredCandidate candidate(String id, double score) {
    return new PersonalizedRankerV1.ScoredCandidate(
        new PersonalizedRankerV1.Candidate(id,
            new PersonalizedRankerV1.Components(score, null, null, null, null)), score,
        new PersonalizedRankerV1.Breakdown(score, 0, 0, 0, 0), 0);
  }

  @Test
  void avoidsNearDuplicateWhenDiversityHasWeight() {
    var ranked = List.of(candidate("a", .95), candidate("b", .9), candidate("c", .7));
    var embeddings = Map.of("a", List.of(1d, 0d), "b", List.of(.99, .01), "c", List.of(0d, 1d));
    var selected = new BookDiversityMmr(new BookDiversityMmr.Config("mmr-v1", .5))
        .select(ranked, embeddings, 2);
    assertEquals(List.of("a", "c"), selected.stream()
        .map(item -> item.candidate().candidate().id()).toList());
    assertEquals(0, selected.get(1).maxSimilarity());
  }

  @Test
  void lambdaOnePreservesRankerOrderAndMissingEmbeddingIsSafe() {
    var ranked = List.of(candidate("a", .8), candidate("b", .7));
    var mmr = new BookDiversityMmr(new BookDiversityMmr.Config("mmr-v1", 1));
    assertEquals(List.of("a", "b"), mmr.select(ranked, Map.of(), 2).stream()
        .map(item -> item.candidate().candidate().id()).toList());
    assertEquals("missing-embedding-fallback", mmr.select(ranked, Map.of(), 1).getFirst().rationale());
  }
}
