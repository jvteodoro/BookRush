package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ReadabilityCalculatorTest {
 @Test void usesLanguageSpecificFormula(){assertTrue(ReadabilityCalculator.calculate("This is a simple sentence.","en").values().containsKey("readability.flesch_en")); assertTrue(ReadabilityCalculator.calculate("Esta é uma frase simples.","pt").values().containsKey("readability.flesch_ptbr"));}
 @Test void unsupportedLanguageIsExplicit(){assertEquals(ReadabilityCalculator.Status.UNSUPPORTED,ReadabilityCalculator.calculate("Une phrase.","fr").status());}
}
