package com.bookrush.analytics.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class NliHypothesisCatalogTest {
  @Test
  void loadsVersionedNarrativeAndEmotionSetsWithoutTranslation() {
    try (InputStream input = getClass().getResourceAsStream("/nli-hypotheses-v1.json")) {
      var catalog = NliHypothesisCatalog.load(input);
      assertEquals("BOOKRUSH_NLI_HYPOTHESES", catalog.code());
      assertEquals("1.0.0", catalog.version());
      assertEquals(16, catalog.forLanguage("en").size());
      assertEquals(16, catalog.forLanguage("pt").size());
      assertEquals("conflict", catalog.forLanguage("en").get(0).key());
      assertTrue(catalog.forLanguage("pt").get(0).text().contains("conflito"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void rejectsMalformedHypothesis() {
    assertThrows(IllegalArgumentException.class, () ->
        new NliHypothesisCatalog.Hypothesis("narrative", "", "text"));
  }
}
