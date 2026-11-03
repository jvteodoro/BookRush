package com.bookrush.analytics.features;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deterministic V2 lexical features over the frozen lemma/token and corpus contracts. */
public final class LexicalFeatureCalculator {
  public static final String VERSION = "lexical-v2-mattr-mtld-frequency-v1";

  private LexicalFeatureCalculator() {}

  public static Result calculate(
      List<String> lemmas,
      CorpusFrequencyModel corpus,
      int mattrWindow,
      double mtldThreshold,
      double rareSurprisalThreshold) {
    if (lemmas == null || corpus == null || mattrWindow < 2 || mtldThreshold <= 0 || mtldThreshold >= 1
        || rareSurprisalThreshold < 0) {
      throw new IllegalArgumentException("invalid lexical feature configuration");
    }
    var tokens = lemmas.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .toList();
    if (tokens.isEmpty()) return new Result(Map.of(), "INSUFFICIENT_SAMPLE", VERSION);

    var values = new LinkedHashMap<String, Double>();
    values.put("unique_lemma_ratio", uniqueRatio(tokens));
    values.put("mattr", mattr(tokens, mattrWindow));
    values.put("mtld", mtld(tokens, mtldThreshold));
    var stats = corpus.summarize(tokens);
    values.put("lexical_frequency_mean", stats.mean());
    values.put("lexical_frequency_median", stats.median());
    values.put("lexical_rarity_p90", stats.p90());
    values.put("rare_word_ratio", tokens.stream().filter(t -> corpus.surprisal(t) >= rareSurprisalThreshold).count() / (double) tokens.size());
    values.put("oov_ratio", tokens.stream().filter(t -> !corpus.contains(t)).count() / (double) tokens.size());
    return new Result(Map.copyOf(values), "VALID", VERSION);
  }

  private static double uniqueRatio(List<String> tokens) {
    return tokens.stream().distinct().count() / (double) tokens.size();
  }

  private static double mattr(List<String> tokens, int window) {
    if (tokens.size() <= window) return uniqueRatio(tokens);
    var total = 0d;
    for (int start = 0; start <= tokens.size() - window; start++) {
      total += uniqueRatio(tokens.subList(start, start + window));
    }
    return total / (tokens.size() - window + 1);
  }

  private static double mtld(List<String> tokens, double threshold) {
    var factors = 0d;
    var types = new java.util.HashSet<String>();
    var factorTokens = 0;
    for (String token : tokens) {
      types.add(token);
      factorTokens++;
      if (types.size() / (double) factorTokens <= threshold) {
        factors++;
        types.clear();
        factorTokens = 0;
      }
    }
    if (factorTokens > 0) {
      double partial = (1d - types.size() / (double) factorTokens) / (1d - threshold);
      factors += Math.max(partial, 0d);
    }
    return factors == 0 ? tokens.size() : tokens.size() / factors;
  }

  public record Result(Map<String, Double> values, String status, String version) {}
}
