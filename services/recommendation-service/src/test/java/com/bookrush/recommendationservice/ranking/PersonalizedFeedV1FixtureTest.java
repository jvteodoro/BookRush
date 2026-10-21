package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
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

  private PersonalizedRankerV1.Candidate candidate(String id, double affinity) {
    return new PersonalizedRankerV1.Candidate(id,
        new PersonalizedRankerV1.Components(affinity, 0d, 0d, 0d, 0d));
  }
}
