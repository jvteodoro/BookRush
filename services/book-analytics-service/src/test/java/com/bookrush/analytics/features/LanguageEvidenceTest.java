package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LanguageEvidenceTest {
  @Test
  void preservesValidPredictionAndCatalogAgreement() {
    var evidence = LanguageEvidence.from(
        "pt", new LinguisticRuntimeClient.LanguageResult("VALID", "pt", .97, null));
    assertEquals("VALID", evidence.status());
    assertEquals("pt", evidence.detectedLanguage());
    assertEquals(.97, evidence.confidence());
    assertEquals(1d, evidence.agreement());
  }

  @Test
  void keepsUnavailableModelExplicitAndDoesNotInventAgreement() {
    var evidence = LanguageEvidence.from(
        "en", new LinguisticRuntimeClient.LanguageResult("MODEL_UNAVAILABLE", "und", 0, "cache missing"));
    assertEquals("MODEL_UNAVAILABLE", evidence.status());
    assertNull(evidence.agreement());
    assertEquals("cache missing", evidence.warning());
  }
}
