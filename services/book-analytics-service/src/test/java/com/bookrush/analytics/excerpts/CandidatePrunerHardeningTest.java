package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CandidatePrunerHardeningTest {
  @Test
  void rejectsBoilerplateAndNearDuplicatesWithReasons() {
    var candidates = List.of(
        new ExcerptCandidateGenerator.Candidate(0, 1, "contents", 1, 1, "h1", "v1"),
        new ExcerptCandidateGenerator.Candidate(
            1, 2, "A quiet garden with flowers and birds in spring.", 8, 1, "h2", "v1"),
        new ExcerptCandidateGenerator.Candidate(
            2, 3, "A quiet garden with flowers and birds in spring today.", 9, 1, "h3", "v1"));

    var result = CandidatePruner.prune(candidates, new CandidatePruner.Config(1, 20, "p2"));

    assertTrue(result.rejected().stream().anyMatch(r -> r.reason().equals("BOILERPLATE")));
    assertTrue(result.rejected().stream().anyMatch(r -> r.reason().equals("NEAR_DUPLICATE")));
  }
}
