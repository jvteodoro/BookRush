package com.bookrush.analytics.emotion;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DisabledEmotionClassifierTest {
 @Test void doesNotTurnUnsupportedIntoZeros() {
  var result=new DisabledEmotionClassifier().classify("texto", "pt");
  assertFalse(result.supported()); assertTrue(result.scores().isEmpty()); assertEquals("emotion-model-disabled", result.model());
 }
}
