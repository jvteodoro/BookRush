package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class LanguageValidationTest { @Test void missingFastTextIsExplicit(){assertEquals(LanguageValidation.Status.MODEL_UNAVAILABLE,LanguageValidation.unavailable().detect("text").status());} @Test void unsupportedLanguageIsNotZero(){assertEquals(LanguageValidation.Status.UNSUPPORTED,LanguageValidation.acceptStructural("fr").status());} }
