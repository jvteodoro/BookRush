package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalizedRankerV1Test {
  private static PersonalizedRankerV1 ranker(PersonalizedRankerV1.MissingPolicy policy) {
    return new PersonalizedRankerV1(new PersonalizedRankerV1.Config(
        "personalized-v1-test", .5, .2, .1, .1, .1, policy));
  }

  @Test
  void scoreChangesWithAffinityAndExposesAllComponents() {
    var candidate = new PersonalizedRankerV1.Candidate("book-a",
        new PersonalizedRankerV1.Components(.9, .5, .2, .1, .8));
    var score = ranker(PersonalizedRankerV1.MissingPolicy.RENORMALIZE).score(candidate, 42);
    assertEquals(.9 * .5 + .5 * .2 + .2 * .1 + .1 * .1 + .8 * .1,
        score.score(), .0001);
    assertEquals(.9, score.breakdown().affinity(), .0001);
    assertNotEquals(score.score(), ranker(PersonalizedRankerV1.MissingPolicy.RENORMALIZE)
        .score(new PersonalizedRankerV1.Candidate("book-a",
            new PersonalizedRankerV1.Components(.1, .5, .2, .1, .8)), 42).score());
  }

  @Test
  void missingFeatureIsRenormalizedExplicitly() {
    var result = ranker(PersonalizedRankerV1.MissingPolicy.RENORMALIZE).score(
        new PersonalizedRankerV1.Candidate("book-a",
            new PersonalizedRankerV1.Components(.8, null, null, null, null)), 1);
    assertEquals(.8, result.score(), .0001);
  }

  @Test
  void sameSeedIsDeterministicAndDifferentSeedChangesTieBreak() {
    var first = new PersonalizedRankerV1.Candidate("book-a",
        new PersonalizedRankerV1.Components(.5, .5, .5, .5, .5));
    var second = new PersonalizedRankerV1.Candidate("book-b",
        new PersonalizedRankerV1.Components(.5, .5, .5, .5, .5));
    var ranker = ranker(PersonalizedRankerV1.MissingPolicy.ZERO);
    assertEquals(ranker.rank(List.of(first, second), 7), ranker.rank(List.of(first, second), 7));
    assertNotEquals(ranker.rank(List.of(first, second), 7), ranker.rank(List.of(first, second), 8));
  }
}
