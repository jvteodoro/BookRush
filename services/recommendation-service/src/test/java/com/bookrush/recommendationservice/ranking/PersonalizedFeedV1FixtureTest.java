package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.bookrush.recommendationservice.retrieval.CandidateUnionBuilder;
import com.bookrush.recommendationservice.taste.UserTasteProfileBuilder;
import org.junit.jupiter.api.Test;

/** Deterministic contract fixture for two profiles and cold-start behaviour. */
class PersonalizedFeedV1FixtureTest {
  private final PersonalizedRankerV1 ranker = new PersonalizedRankerV1(
      new PersonalizedRankerV1.Config("personalized-v1", 1, 0, 0, 0, 0,
          PersonalizedRankerV1.MissingPolicy.ZERO));

  @Test
  void differentHistoriesChangeAffinityOrderingAndColdStartHasNoFabricatedAffinity() {
    var books = List.of("semantic-a", "semantic-b", "global-c");
    var profileA = ranker.rank(List.of(
        candidate(books.get(0), .95), candidate(books.get(1), .10), candidate(books.get(2), 0)), 11);
    var profileB = ranker.rank(List.of(
        candidate(books.get(0), .10), candidate(books.get(1), .95), candidate(books.get(2), 0)), 11);
    var cold = ranker.rank(List.of(
        candidate(books.get(0), 0), candidate(books.get(1), 0), candidate(books.get(2), 0)), 11);

    assertEquals("semantic-a", profileA.get(0).candidate().id());
    assertEquals("semantic-b", profileB.get(0).candidate().id());
    assertNotEquals(profileA.get(0).candidate().id(), profileB.get(0).candidate().id());
    assertEquals(0d, cold.get(0).breakdown().affinity());
    assertEquals(0d, cold.get(1).breakdown().affinity());
    assertEquals(0d, cold.get(2).breakdown().affinity());
  }

  @Test
  void buildsProfileAndRanksUnseenCandidatesFromRealEmbeddingCosine() {
    UUID seenA = UUID.randomUUID();
    UUID seenB = UUID.randomUUID();
    var builder = new UserTasteProfileBuilder(new UserTasteProfileBuilder.Config(
        "taste-fixture", java.time.Duration.ofDays(30), 10, 4, 1));
    var now = Instant.parse("2026-11-03T00:00:00Z");
    var profileA = builder.build(List.of(new UserTasteProfileBuilder.Signal(
        seenA, 0, 1, 0, 0, 0, false, false, now)),
        Map.of(seenA, embedding(seenA, List.of(1d, 0d))), now);
    var profileB = builder.build(List.of(new UserTasteProfileBuilder.Signal(
        seenB, 0, 1, 0, 0, 0, false, false, now)),
        Map.of(seenB, embedding(seenB, List.of(0d, 1d))), now);

    double aToA = CandidateUnionBuilder.cosine(profileA.vector(), List.of(1d, 0d));
    double aToB = CandidateUnionBuilder.cosine(profileA.vector(), List.of(0d, 1d));
    double bToB = CandidateUnionBuilder.cosine(profileB.vector(), List.of(0d, 1d));
    double bToA = CandidateUnionBuilder.cosine(profileB.vector(), List.of(1d, 0d));

    assertEquals(1d, aToA, .0001);
    assertEquals(0d, aToB, .0001);
    assertEquals(1d, bToB, .0001);
    assertEquals(0d, bToA, .0001);
    assertEquals("a-unseen", rank("a-unseen", "b-unseen", aToA, aToB).getFirst());
    assertEquals("b-unseen", rank("b-unseen", "a-unseen", bToB, bToA).getFirst());
  }

  private UserTasteProfileBuilder.BookEmbedding embedding(UUID id, List<Double> vector) {
    return new UserTasteProfileBuilder.BookEmbedding(id, "BGE-M3", "v1", UUID.randomUUID(),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", vector);
  }

  private List<String> rank(String firstId, String secondId, double first, double second) {
    return new PersonalizedRankerV1(new PersonalizedRankerV1.Config("cosine-fixture", 1, 0, 0, 0, 0,
            PersonalizedRankerV1.MissingPolicy.ZERO))
        .rank(List.of(new PersonalizedRankerV1.Candidate(firstId,
                new PersonalizedRankerV1.Components(first, 0d, 0d, 0d, 0d)),
            new PersonalizedRankerV1.Candidate(secondId,
                new PersonalizedRankerV1.Components(second, 0d, 0d, 0d, 0d))), 1)
        .stream().map(candidate -> candidate.candidate().id()).toList();
  }

  private PersonalizedRankerV1.Candidate candidate(String id, double affinity) {
    return new PersonalizedRankerV1.Candidate(id,
        new PersonalizedRankerV1.Components(affinity, 0d, 0d, 0d, 0d));
  }
}
