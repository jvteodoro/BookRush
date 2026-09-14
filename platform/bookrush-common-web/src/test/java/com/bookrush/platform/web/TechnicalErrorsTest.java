package com.bookrush.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TechnicalErrorsTest {
  @Test
  void usesStableMessagesWithoutInternalDetails() {
    var response = TechnicalErrors.safe("IDEMPOTENCY_CONFLICT", "corr-1");
    assertEquals("IDEMPOTENCY_CONFLICT", response.code());
    assertEquals("corr-1", response.correlationId());
  }

  @Test
  void rejectsHeaderInjection() {
    assertThrows(IllegalArgumentException.class, () -> new TechnicalErrorResponse("X", "safe", "bad\nheader"));
  }
}
