package com.bookrush.analytics.features;

import java.util.*;

/**
 * Language validation boundary. The concrete fastText adapter is supplied only when lid.176.bin is
 * prepared.
 */
public final class LanguageValidation {
  private LanguageValidation() {}

  public enum Status {
    VALID,
    UNSUPPORTED,
    MODEL_UNAVAILABLE,
    INVALID_INPUT
  }

  public record Result(String language, double confidence, Status status, String warning) {}

  public interface Validator {
    Result detect(String text);
  }

  public static Validator unavailable() {
    return text ->
        new Result(null, 0, Status.MODEL_UNAVAILABLE, "fastText lid.176.bin is not prepared");
  }

  public static Result acceptStructural(String declaredLanguage) {
    if (declaredLanguage == null || declaredLanguage.isBlank())
      return new Result(null, 0, Status.INVALID_INPUT, "language is required");
    String normalized = declaredLanguage.trim().toLowerCase(Locale.ROOT);
    return Set.of("en", "pt").contains(normalized)
        ? new Result(normalized, 1, Status.VALID, null)
        : new Result(
            normalized,
            1,
            Status.UNSUPPORTED,
            "linguistic models are limited to en and pt");
  }

  /** Rejects empty input before invoking a model, preserving an explicit status instead of zero. */
  public static Result validateInputText(String text) {
    return text == null || text.isBlank()
        ? new Result(null, 0, Status.INVALID_INPUT, "text is required")
        : null;
  }
}
