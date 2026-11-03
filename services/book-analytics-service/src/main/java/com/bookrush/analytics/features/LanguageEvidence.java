package com.bookrush.analytics.features;

/** Immutable language-validation evidence; it never changes catalog metadata. */
public record LanguageEvidence(
    String status,
    String detectedLanguage,
    double confidence,
    String catalogLanguage,
    Double agreement,
    String warning) {

  public static LanguageEvidence from(
      String catalogLanguage, LinguisticRuntimeClient.LanguageResult result) {
    String status = switch (result.status()) {
      case "VALID", "MODEL_UNAVAILABLE", "INVALID_INPUT", "INSUFFICIENT_SAMPLE", "ERROR" -> result.status();
      default -> "UNSUPPORTED";
    };
    Double agreement = "VALID".equals(status)
        && catalogLanguage != null
        && result.language() != null
        ? (catalogLanguage.equalsIgnoreCase(result.language()) ? 1d : 0d)
        : null;
    return new LanguageEvidence(
        status,
        result.language(),
        result.confidence(),
        catalogLanguage,
        agreement,
        result.warning());
  }
}
