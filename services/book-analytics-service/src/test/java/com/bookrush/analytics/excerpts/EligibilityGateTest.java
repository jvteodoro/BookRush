package com.bookrush.analytics.excerpts;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class EligibilityGateTest {
 @Test void rejectsInvalidAndAcceptsValid() {
  var bounds=new EligibilityGate.Bounds(20,250,0.5);
  assertFalse(EligibilityGate.evaluate(Map.of("word_count",30d),Map.of("nlp","MODEL_UNAVAILABLE"),bounds).eligible());
  assertTrue(EligibilityGate.evaluate(Map.of("word_count",30d,"narrative.autonomy",.8),Map.of("nlp","VALID"),bounds).eligible());
 }
}
