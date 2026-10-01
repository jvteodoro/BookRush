package com.bookrush.analytics.features;

import java.util.*;

/** Protocol guardrails for optional NLI: bounded candidates, raw E/N/C and explicit status. */
public final class NarrativeInferenceProtocol {
  private NarrativeInferenceProtocol() {}

  public static final int DEFAULT_MAX_EXCERPTS = 50;

  public static Selection select(List<String> excerptIds, int max) {
    if (max < 1) throw new IllegalArgumentException("max must be positive");
    return new Selection(
        List.copyOf(excerptIds.subList(0, Math.min(max, excerptIds.size()))),
        excerptIds.size() > max);
  }

  public static Observation observation(
      String concept, String language, NliScore score, String modelVersion) {
    return new Observation(
        concept,
        language,
        score.entailment(),
        score.neutral(),
        score.contradiction(),
        score.support(),
        score.confidence(),
        modelVersion,
        "MODEL_SCORE");
  }

  public record Selection(List<String> excerptIds, boolean truncated) {}

  public record Observation(
      String concept,
      String language,
      double entailment,
      double neutral,
      double contradiction,
      double support,
      double confidence,
      String modelVersion,
      String semanticCategory) {}
}
