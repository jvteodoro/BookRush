package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalizedFeedSelectorTest {
  @Test
  void semanticCandidateOutsideGlobalQualityTopNCanBePromoted() {
    var candidates = List.of(
        candidate("A", .10, .99), candidate("B", .10, .98), candidate("C", .10, .97),
        candidate("D", .99, .30), candidate("E", .90, .20));
    var globalTop = candidates.stream().sorted((left, right) ->
        Double.compare(right.components().quality(), left.components().quality()))
        .limit(3).map(PersonalizedRankerV1.Candidate::id).toList();
    var selector = new PersonalizedFeedSelector(
        new PersonalizedRankerV1(new PersonalizedRankerV1.Config("fixture", 1, 0, 0, 0, 0,
            PersonalizedRankerV1.MissingPolicy.ZERO)),
        new ExplorationPolicy(new ExplorationPolicy.Config("off", false, .1, 1)),
        new BookDiversityMmr(new BookDiversityMmr.Config("fixture-mmr", 1)));

    var selected = selector.select(candidates, Map.of(), 3, 44).selections().stream()
        .map(selection -> selection.candidate().candidate().id()).toList();

    assertFalse(globalTop.contains("D"));
    assertEquals(3, selected.size());
    assertTrue(selected.contains("D"));
  }

  @Test
  void missingQualityDoesNotExcludeAHighAffinityCandidate() {
    var selector = new PersonalizedFeedSelector(
        new PersonalizedRankerV1(new PersonalizedRankerV1.Config("missing-quality", .8, .2, 0, 0, 0,
            PersonalizedRankerV1.MissingPolicy.RENORMALIZE)),
        new ExplorationPolicy(new ExplorationPolicy.Config("off", false, .1, 1)),
        new BookDiversityMmr(new BookDiversityMmr.Config("missing-quality-mmr", 1)));
    var result = selector.select(List.of(
        new PersonalizedRankerV1.Candidate("semantic", new PersonalizedRankerV1.Components(.99, null, null, null, 1d)),
        new PersonalizedRankerV1.Candidate("other", new PersonalizedRankerV1.Components(.10, .90, 0d, null, 1d))),
        Map.of(), 1, 7);

    assertTrue(result.selections().stream()
        .anyMatch(selection -> selection.candidate().candidate().id().equals("semantic")));
  }

  @Test
  void explorationReservesAnExactSlotOutsideTheExploitationPrefix() {
    var selector = new PersonalizedFeedSelector(
        new PersonalizedRankerV1(new PersonalizedRankerV1.Config("exploration", 1, 0, 0, 0, 0,
            PersonalizedRankerV1.MissingPolicy.ZERO)),
        new ExplorationPolicy(new ExplorationPolicy.Config("explore-v1", true, .5, 1)),
        new BookDiversityMmr(new BookDiversityMmr.Config("exploration-mmr", 1)));

    var result = selector.select(List.of(
        candidate("relevant-a", 1, .1), candidate("relevant-b", .9, .1),
        candidate("discovery-c", .1, .99), candidate("discovery-d", .05, .98)),
        Map.of(), 2, 17);

    assertEquals(2, result.selections().size());
    assertEquals(1, result.explorationIds().size());
    assertTrue(result.explorationIds().stream().anyMatch(id -> id.startsWith("discovery-")));
    assertTrue(result.selections().stream()
        .anyMatch(selection -> result.explorationIds().contains(selection.candidate().candidate().id())));
  }

  private static PersonalizedRankerV1.Candidate candidate(String id, double affinity, double quality) {
    return new PersonalizedRankerV1.Candidate(id,
        new PersonalizedRankerV1.Components(affinity, quality, 0d, null, 1d));
  }
}
