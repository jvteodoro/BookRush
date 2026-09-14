package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
class StatisticalFeatureCalculatorTest {
 @Test void returnsReproducibleLanguageAwareStatistics() {
  var first=StatisticalFeatureCalculator.calculate("This is a short text. Text has rhythm.", Set.of("this","is","a"));
  var second=StatisticalFeatureCalculator.calculate("This is a short text. Text has rhythm.", Set.of("this","is","a"));
  assertEquals(first,second); assertEquals(7d/8d, first.get("lexical_diversity")); assertEquals(3d/8d, first.get("stopword_ratio"));
 }
 @Test void emptyInputHasUnsupportedFeatureSet() { assertTrue(StatisticalFeatureCalculator.calculate("", Set.of()).isEmpty()); }
}
