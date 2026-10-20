package com.bookrush.analytics.llm;

import com.bookrush.analytics.features.LinguisticRuntimeClient;
import java.util.ArrayList;
import java.util.HashMap;
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
    return inferBatch(List.of(new Input("single", text, language)), 8).stream()
        .map(ObservationWithId::observation).toList();
  }

  public List<ObservationWithId> inferBatch(List<Input> inputs, int batchSize) {
    if (inputs == null || inputs.isEmpty()) return List.of();
    var requests = new ArrayList<LinguisticRuntimeClient.NliBatchInput>();
    var lookup = new HashMap<String, NliHypothesisCatalog.Hypothesis>();
    for (var input : inputs) {
      for (var hypothesis : catalog.forLanguage(input.language())) {
        String id = input.id() + ":" + hypothesis.kind() + ":" + hypothesis.key();
        requests.add(new LinguisticRuntimeClient.NliBatchInput(id, input.text(), hypothesis.text()));
        lookup.put(id, hypothesis);
      }
    }
    var results = new ArrayList<LinguisticRuntimeClient.NliBatchResult>();
    int requestChunkSize = Math.max(1, Math.min(32, batchSize));
    for (int offset = 0; offset < requests.size(); offset += requestChunkSize) {
      results.addAll(runtime.nliBatch(
          requests.subList(offset, Math.min(offset + requestChunkSize, requests.size())), requestChunkSize));
    }
    return results.stream().map(result -> {
      var hypothesis = lookup.get(result.id());
      var score = result.score();
      var observation = new Observation(catalog.code(), catalog.version(), hypothesis == null ? "unknown" : hypothesis.kind(),
          hypothesis == null ? "unknown" : hypothesis.key(), result.status(), score.entailment(),
          score.neutral(), score.contradiction(), score.support(), score.confidence(), result.warning());
      String sourceId = result.id().contains(":") ? result.id().substring(0, result.id().indexOf(':')) : result.id();
      return new ObservationWithId(sourceId, observation);
    }).toList();
  }

  public record Input(String id, String text, String language) {}

  public record ObservationWithId(String id, Observation observation) {}

  public record Observation(
      String hypothesisSetCode, String hypothesisSetVersion, String kind, String key,
      String status, double entailment, double neutral, double contradiction,
      double support, double confidence, String warning) {}
}
