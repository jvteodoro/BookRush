package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalizedDecisionReplayTest {
  @Test
  void sameDecisionProducesSameOrderHashAndPreservesProvenance() {
    var ranker = new PersonalizedRankerV1(new PersonalizedRankerV1.Config(
        "v1", 1, 0, 0, 0, 0, PersonalizedRankerV1.MissingPolicy.ZERO));
    var ranked = ranker.rank(List.of(
        new PersonalizedRankerV1.Candidate("b", new PersonalizedRankerV1.Components(.2, null, null, null, null)),
        new PersonalizedRankerV1.Candidate("a", new PersonalizedRankerV1.Components(.9, null, null, null, null))), 7);
    var replay = new PersonalizedDecisionReplay().replay(ranked, "profile-v1", "candidate-hash", "ranker-hash");
    var again = new PersonalizedDecisionReplay().replay(ranked, "profile-v1", "candidate-hash", "ranker-hash");
    assertEquals(List.of("a", "b"), replay.orderedBookIds());
    assertEquals(replay.orderHash(), again.orderHash());
    assertEquals("profile-v1", replay.profileVersion());
    assertEquals("candidate-hash", replay.candidateSetHash());
  }
}
