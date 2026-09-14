package com.bookrush.platform.web;

import com.bookrush.platform.core.CorrelationId;

/** Wire-neutral error value. Services decide status and serialization compatibility. */
public record TechnicalErrorResponse(String code, String message, String correlationId) {
  public TechnicalErrorResponse {
    if (code == null || code.isBlank() || code.length() > 80 || code.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("invalid error code");
    }
    if (message == null || message.isBlank() || message.length() > 256 || message.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("invalid error message");
    }
    if (correlationId != null) new CorrelationId(correlationId);
  }
}
