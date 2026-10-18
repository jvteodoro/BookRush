package com.bookrush.analytics.excerpts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies technical eligibility before deterministic MMR selection. */
public final class CandidateSelectionPipeline {
  private CandidateSelectionPipeline() {}

  public static Result select(
      List<Input> inputs,
      EligibilityGate.Bounds bounds,
      String registeredEmbeddingModel,
      int limit,
      double relevanceWeight,
      double diversityWeight) {
    if (registeredEmbeddingModel == null || registeredEmbeddingModel.isBlank())
      throw new IllegalArgumentException("registered embedding model is required");
    var eligible = new ArrayList<MMRDiversitySelector.Candidate>();
    var rejected = new ArrayList<Rejected>();
    for (var input : inputs) {
      var gate = EligibilityGate.evaluate(input.features(), input.statuses(), bounds);
      if (!gate.eligible()) {
        rejected.add(new Rejected(input.id(), gate.exclusionReason()));
        continue;
      }
      if (!registeredEmbeddingModel.equals(input.embeddingModel())) {
        rejected.add(new Rejected(input.id(), "EMBEDDING_MODEL_MISMATCH"));
        continue;
      }
      if (input.embedding() == null) {
        rejected.add(new Rejected(input.id(), "EMBEDDING_UNAVAILABLE"));
        continue;
      }
      eligible.add(new MMRDiversitySelector.Candidate(input.id(), input.relevance(), input.embedding()));
    }
    return new Result(
        MMRDiversitySelector.select(
            eligible, limit, relevanceWeight, diversityWeight),
        List.copyOf(rejected));
  }

  public record Input(
      String id,
      double relevance,
      Map<String, Double> features,
      Map<String, String> statuses,
      float[] embedding,
      String embeddingModel) {}

  public record Rejected(String id, String reason) {}

  public record Result(List<MMRDiversitySelector.Candidate> selected, List<Rejected> rejected) {}
}
