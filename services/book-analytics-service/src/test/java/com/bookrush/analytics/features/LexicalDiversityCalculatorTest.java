package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class LexicalDiversityCalculatorTest {
 @Test void computesVersionedMetricsAndWarnsOnShortText(){var r=LexicalDiversityCalculator.calculate("One two two três."); assertEquals(.75d,r.values().get("lex.ttr")); assertEquals(LexicalDiversityCalculator.Status.INSUFFICIENT_SAMPLE,r.status());}
}
