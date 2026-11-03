package com.bookrush.recommendationservice.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bookrush.recommendationservice.taste.UserTasteProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateUnionBuilderTest {
  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

  @Test
  void usesSemanticCosineAndKeepsAllContributingSourcesAfterDeduplication() {
    UUID shared = UUID.randomUUID();
    UUID trending = UUID.randomUUID();
    var sharedBook = Map.<String, Object>of("id", shared, "embedding", List.of(1d, 0d));
    var profile = new UserTasteProfile(UserTasteProfile.Status.READY, List.of(1d, 0d),
        "taste-v1", "config", 1, 1, 1, NOW);
    var config = new CandidateUnionBuilder.Config("union-v1", 3, 2, 1, 1, 1);
    var result = new CandidateUnionBuilder(config).build(profile, List.of(
        new CandidateUnionBuilder.Source("trending", List.of(
            new CandidateUnionBuilder.Candidate(Map.of("id", trending), .8))),
        new CandidateUnionBuilder.Source("semantic", List.of(
            new CandidateUnionBuilder.Candidate(sharedBook, .1))),
        new CandidateUnionBuilder.Source("global", List.of(
            new CandidateUnionBuilder.Candidate(sharedBook, .2)))));

    assertEquals(2, result.books().size());
    assertEquals(List.of("global", "semantic"), result.sourcesFor(shared.toString()));
    assertTrue(result.sourcesFor(trending.toString()).contains("trending"));
    assertEquals(1, CandidateUnionBuilder.cosine(List.of(1d, 0d), List.of(1d, 0d)));
  }

  @Test
  void coldStartDoesNotRequireAUserVectorAndUnionIsBounded() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var result = new CandidateUnionBuilder(new CandidateUnionBuilder.Config(
        "union-v1", 1, 1, 1, 1, 1)).build(null, List.of(
            new CandidateUnionBuilder.Source("global", List.of(
                new CandidateUnionBuilder.Candidate(Map.of("id", first), .5),
                new CandidateUnionBuilder.Candidate(Map.of("id", second), .4))),
            new CandidateUnionBuilder.Source("exploration", List.of(
                new CandidateUnionBuilder.Candidate(Map.of("id", second), .9)))));

    assertEquals(1, result.books().size());
    assertEquals(first.toString(), result.canonicalBookIds().getFirst());
  }
}
