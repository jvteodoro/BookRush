package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TextFeatureCalculatorTest {
  @Test void calculatesStableFeaturesForUnicodeDialogueAndQuestions() {
    var f = TextFeatureCalculator.calculate("“Olá😀?”\n\nA resposta!", 10, 100, 5, 50);
    assertEquals(3d, f.get("word_count"));
    assertEquals(2d, f.get("sentence_count"));
    assertEquals(1d, f.get("question_count"));
    assertEquals(1d, f.get("exclamation_count"));
    assertEquals(.1d, f.get("relative_position_in_document"));
  }
  @Test void isDeterministicAndBoundsPositions() {
    var text = "Uma frase.";
    assertEquals(TextFeatureCalculator.calculate(text, 100, 2, -1, 0), TextFeatureCalculator.calculate(text, 100, 2, -1, 0));
    assertEquals(1d, TextFeatureCalculator.calculate(text, 100, 2, -1, 0).get("relative_position_in_document"));
  }
}
