package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidatePrunerV2Test {
  @Test
  void preservesV2ChapterLineageAndReportsTechnicalRejections() {
    String text = "One two three four five. One two three four five. One two three four five.";
    var chapter =
        new ExcerptCandidateGenerator.ChapterBoundary(
            UUID.randomUUID(), 0, text.codePointCount(0, text.length()));
    var config =
        new ExcerptCandidateGenerator.V2Config(
            5, List.of(new ExcerptCandidateGenerator.WindowProfile(5, 1)), 30, false, "v2");
    var candidates = ExcerptCandidateGenerator.generateV2(text, List.of(chapter), config);

    var result = CandidatePruner.pruneV2(candidates, new CandidatePruner.Config(6, 30, "cheap-v2"));

    assertEquals("cheap-v2", result.configVersion());
    assertTrue(
        result.accepted().stream()
            .allMatch(candidate -> candidate.chapterId().equals(chapter.chapterId())));
    assertTrue(
        result.rejected().stream()
            .allMatch(
                rejection ->
                    rejection.reason().equals("TOO_SHORT")
                        || rejection.reason().equals("DUPLICATE")));
  }
}
