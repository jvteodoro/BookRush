package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TextFeatureCalculatorV2Test {
  @Test
  void calculatesDeterministicRhythmAndPositionMeasurements() {
    String text =
        "Curta.\n\n"
            + "Uma frase com exatamente nove palavras aqui agora. Uau!\n\n"
            + "Uma frase muito muito muito longa para mudar o ritmo.";
    Map<String, Double> features = TextFeatureCalculator.calculate(text, 10, 100, 5, 50);

    assertEquals(3d, features.get("paragraph_count"));
    assertEquals(4d, features.get("sentence_count"));
    assertEquals(.25d, features.get("exclamation_ratio"));
    assertEquals(0.1d, features.get("book_relative_position"));
    assertEquals(0.1d, features.get("chapter_relative_position"));
    assertTrue(features.get("sentence_length_std") > 0d);
    assertTrue(features.get("sentence_length_cv") > 0d);
    assertTrue(features.get("sentence_length_delta_mean") > 0d);
    assertTrue(features.get("short_sentence_ratio") > 0d);
    assertTrue(features.get("short_sentence_burst") >= 1d);
    assertTrue(features.get("paragraph_length_cv") > 0d);
  }

  @Test
  void detectsAsciiAndUnicodeEllipsesAndDashesWithoutAffectingCodePointMath() {
    var features = TextFeatureCalculator.calculate("Olá😀... — espere… — agora!", 0, 30, 0, 30);

    assertEquals(0d, features.get("book_relative_position"));
    assertTrue(features.get("ellipsis_density") > 0d);
    assertTrue(features.get("dash_density") > 0d);
    assertTrue(features.get("punctuation_density") > 0d);
  }

  @Test
  void isDeterministicForSingleSentenceAndAvoidsNaN() {
    var features = TextFeatureCalculator.calculate("Uma só sentença.", 0, 18, 0, 18);

    assertEquals(0d, features.get("sentence_length_std"));
    assertEquals(0d, features.get("sentence_length_cv"));
    assertEquals(0d, features.get("sentence_length_delta_mean"));
    assertTrue(features.values().stream().allMatch(Double::isFinite));
  }
}
