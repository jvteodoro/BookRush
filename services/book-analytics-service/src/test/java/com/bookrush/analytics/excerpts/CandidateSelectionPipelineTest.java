package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CandidateSelectionPipelineTest {
  private static final EligibilityGate.Bounds BOUNDS = new EligibilityGate.Bounds(20, 250, 0.5);

  @Test
  void gatesBeforeMmrAndKeepsEmbeddingLineage() {
    var valid =
        new CandidateSelectionPipeline.Input(
            "a", .9, Map.of("word_count", 40d, "narrative.autonomy", .8), Map.of(),
            new float[] {1, 0}, "bge-m3-dense-v1");
    var wrongModel =
        new CandidateSelectionPipeline.Input(
            "b", .8, Map.of("word_count", 40d, "narrative.autonomy", .8), Map.of(),
            new float[] {0, 1}, "other-model");
    var tooShort =
        new CandidateSelectionPipeline.Input(
            "c", .7, Map.of("word_count", 10d), Map.of(), new float[] {0, 1}, "bge-m3-dense-v1");

    var result =
        CandidateSelectionPipeline.select(
            List.of(valid, wrongModel, tooShort), BOUNDS, "bge-m3-dense-v1", 2, 1, 1);

    assertEquals(List.of("a"), result.selected().stream().map(MMRDiversitySelector.Candidate::id).toList());
    assertEquals(
        Map.of("b", "EMBEDDING_MODEL_MISMATCH", "c", "TOO_SHORT"),
        result.rejected().stream().collect(java.util.stream.Collectors.toMap(
            CandidateSelectionPipeline.Rejected::id, CandidateSelectionPipeline.Rejected::reason)));
  }
}
