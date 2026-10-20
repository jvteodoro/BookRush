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

  @Test
  void rejectsEmptyAndMismatchedEmbeddingDimensionsWithoutEnteringMmr() {
    var valid = input("valid", new float[] {1, 0});
    var empty = input("empty", new float[] {});
    var mismatched = input("mismatched", new float[] {1, 0, 0});

    var result =
        CandidateSelectionPipeline.select(
            List.of(valid, empty, mismatched), BOUNDS, "bge-m3-dense-v1", 3, 1, 1);

    assertEquals(List.of("valid"), result.selected().stream().map(MMRDiversitySelector.Candidate::id).toList());
    assertEquals(
        Map.of("empty", "EMBEDDING_EMPTY", "mismatched", "EMBEDDING_DIMENSION_MISMATCH"),
        result.rejected().stream().collect(java.util.stream.Collectors.toMap(
            CandidateSelectionPipeline.Rejected::id, CandidateSelectionPipeline.Rejected::reason)));
  }

  @Test
  void replayWithSameInputsKeepsOrderAndSelectionScores() {
    var inputs =
        List.of(input("z", new float[] {1, 0}), input("a", new float[] {1, 0}), input("m", new float[] {0, 1}));

    var first = CandidateSelectionPipeline.select(inputs, BOUNDS, "bge-m3-dense-v1", 3, 1, 1);
    var replay = CandidateSelectionPipeline.select(inputs, BOUNDS, "bge-m3-dense-v1", 3, 1, 1);

    assertEquals(
        first.selected().stream().map(MMRDiversitySelector.Candidate::id).toList(),
        replay.selected().stream().map(MMRDiversitySelector.Candidate::id).toList());
    assertArrayEquals(first.selected().stream().mapToDouble(MMRDiversitySelector.Candidate::selectionScore).toArray(),
        replay.selected().stream().mapToDouble(MMRDiversitySelector.Candidate::selectionScore).toArray());
    assertEquals(List.of("a", "m", "z"), first.selected().stream().map(MMRDiversitySelector.Candidate::id).toList());
  }

  private static CandidateSelectionPipeline.Input input(String id, float[] embedding) {
    return new CandidateSelectionPipeline.Input(
        id, .8, Map.of("word_count", 40d, "narrative.autonomy", .8), Map.of(), embedding,
        "bge-m3-dense-v1");
  }
}
