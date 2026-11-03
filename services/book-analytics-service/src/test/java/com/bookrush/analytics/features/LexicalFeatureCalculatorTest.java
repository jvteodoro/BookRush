package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LexicalFeatureCalculatorTest {
  @Test
  void calculatesVersionedDiversityFrequencyRarityAndOovFeatures() {
    var corpus = CorpusFrequencyBuilder.toModel(
        CorpusFrequencyBuilder.build("en", "fixture-v2", List.of("red fox runs", "blue fox"), 0.5));
    var result = LexicalFeatureCalculator.calculate(
        List.of("red", "fox", "fox", "runs", "unknown"), corpus, 3, .72, 1.0);

    assertEquals("VALID", result.status());
    assertEquals("lexical-v2-mattr-mtld-frequency-v1", result.version());
    assertTrue(result.values().get("unique_lemma_ratio") > 0d);
    assertTrue(result.values().get("mattr") > 0d);
    assertTrue(result.values().get("mtld") > 0d);
    assertTrue(result.values().get("lexical_rarity_p90") >= result.values().get("lexical_frequency_median"));
    assertEquals(.2d, result.values().get("oov_ratio"), 1e-9);
    assertTrue(result.values().get("rare_word_ratio") > 0d);
  }

  @Test
  void reportsInsufficientSampleWithoutFabricatingValues() {
    var corpus = CorpusFrequencyBuilder.toModel(
        CorpusFrequencyBuilder.build("pt", "fixture-v2", List.of("casa azul"), .5));
    var result = LexicalFeatureCalculator.calculate(List.of(), corpus, 3, .72, 1.0);
    assertEquals("INSUFFICIENT_SAMPLE", result.status());
    assertTrue(result.values().isEmpty());
  }
}
