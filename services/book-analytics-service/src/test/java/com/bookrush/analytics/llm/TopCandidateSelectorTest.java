package com.bookrush.analytics.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopCandidateSelectorTest {
  @Test
  void selectsDeterministicTopKWithoutExceedingLimit() {
    var one = new ExcerptCandidateGenerator.Candidate(0, 1, "one", 1, 1, "one-hash", "v1");
    var two = new ExcerptCandidateGenerator.Candidate(1, 2, "two", 1, 1, "two-hash", "v1");
    var scores = Map.of(
        UUID.nameUUIDFromBytes(one.textSha256().getBytes()), .2d,
        UUID.nameUUIDFromBytes(two.textSha256().getBytes()), .9d);

    var selected = TopCandidateSelector.topK(List.of(one, two), scores, 1);

    assertEquals(List.of(two), selected);
  }
}
