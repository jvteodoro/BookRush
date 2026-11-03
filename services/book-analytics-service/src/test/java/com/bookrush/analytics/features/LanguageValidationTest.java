package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LanguageValidationTest {
  @Test
  void missingFastTextIsExplicit() {
    assertEquals(
        LanguageValidation.Status.MODEL_UNAVAILABLE,
        LanguageValidation.unavailable().detect("text").status());
  }

  @Test
  void unsupportedLanguageIsNotZero() {
    assertEquals(
        LanguageValidation.Status.UNSUPPORTED, LanguageValidation.acceptStructural("fr").status());
  }

  @Test
  void normalizesLanguageAndRejectsBlankText() {
    var result = LanguageValidation.acceptStructural(" PT ");
    assertEquals("pt", result.language());
    assertEquals(LanguageValidation.Status.VALID, result.status());

    var invalid = LanguageValidation.validateInputText(" \n\t");
    assertEquals(LanguageValidation.Status.INVALID_INPUT, invalid.status());
    assertEquals(0, invalid.confidence());
  }

  @Test
  void nullLanguageAndTextRemainExplicitlyInvalid() {
    assertEquals(
        LanguageValidation.Status.INVALID_INPUT, LanguageValidation.acceptStructural(null).status());
    assertEquals(
        LanguageValidation.Status.INVALID_INPUT, LanguageValidation.validateInputText(null).status());
  }
}
