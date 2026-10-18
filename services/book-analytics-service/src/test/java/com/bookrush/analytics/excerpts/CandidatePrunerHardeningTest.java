package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CandidatePrunerHardeningTest {
  private static final CandidatePruner.Config CONFIG = new CandidatePruner.Config(1, 100, "test");

  @Test
  void rejectsBoilerplateAndNearDuplicatesWithAuditableReasons() {
    var first = new ExcerptCandidateGenerator.Candidate(0, 1, "A quiet room with a view overlooking the garden", 9, 1, "h1", "v1");
    var near = new ExcerptCandidateGenerator.Candidate(0, 2, "A quiet room with a view overlooking the garden today", 10, 1, "h2", "v1");
    var contents = new ExcerptCandidateGenerator.Candidate(0, 3, "Contents", 1, 1, "h3", "v1");

    var result = CandidatePruner.prune(List.of(first, near, contents), CONFIG);

    assertEquals(1, result.accepted().size());
    assertEquals("NEAR_DUPLICATE", result.rejected().get(0).reason());
    assertEquals("BOILERPLATE", result.rejected().get(1).reason());
  }
}
