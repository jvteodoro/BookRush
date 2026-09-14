package com.bookrush.platform.web;

/** Fixed messages for transport-level conflicts; domain handlers remain local. */
public final class TechnicalErrors {
  private TechnicalErrors() {}

  public static TechnicalErrorResponse safe(String code, String correlationId) {
    String safeCode = code == null || code.isBlank() ? "TECHNICAL_ERROR" : code;
    String message = switch (safeCode) {
      case "CANONICAL_CONFLICT" -> "The canonical operation conflicts with the current resource state.";
      case "IDEMPOTENCY_CONFLICT" -> "The idempotency key was already used with a different request.";
      default -> "The request could not be completed.";
    };
    return new TechnicalErrorResponse(safeCode, message, correlationId);
  }
}
