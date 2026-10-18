package com.bookrush.analytics.llm;

import com.bookrush.analytics.features.LinguisticRuntimeClient;
import java.util.List;

/** Calls the local NLI runtime and preserves raw E/N/C plus derived support/confidence. */
public final class NliInferenceAdapter {
  private final LinguisticRuntimeClient runtime;
  private final NliHypothesisCatalog catalog;

  public NliInferenceAdapter(LinguisticRuntimeClient runtime, NliHypothesisCatalog catalog) {
    this.runtime = runtime;
    this.catalog = catalog;
  }

  public String hypothesisSetCode() { return catalog.code(); }

  public String hypothesisSetVersion() { return catalog.version(); }

  public List<Observation> infer(String text, String language) {
    return catalog.forLanguage(language).stream().map(hypothesis -> {
      var result = runtime.nli(text, hypothesis.text());
      var score = result.score();
      return new Observation(catalog.code(), catalog.version(), hypothesis.kind(), hypothesis.key(),
          result.status(), score.entailment(), score.neutral(), score.contradiction(),
          score.support(), score.confidence(), result.warning());
    }).toList();
  }

  public record Observation(
      String hypothesisSetCode, String hypothesisSetVersion, String kind, String key,
      String status, double entailment, double neutral, double contradiction,
      double support, double confidence, String warning) {}
}
