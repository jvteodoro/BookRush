package com.bookrush.platform.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TechnicalPrimitivesTest {
  @Test
  void identityKeepsSubjectOpaque() {
    var identity = new TechnicalIdentity("https://issuer", "not-an-email-or-uuid");
    assertEquals("not-an-email-or-uuid", identity.subject());
  }

  @Test
  void correlationRejectsHeaderInjectionAndOversizeValues() {
    assertThrows(IllegalArgumentException.class, () -> new CorrelationId("abc\nX-Injected: yes"));
    assertThrows(IllegalArgumentException.class, () -> new CorrelationId("a".repeat(129)));
  }

  @Test
  void sha256IsDeterministicAndBounded() throws Exception {
    var input = new ByteArrayInputStream("BookRush".getBytes(StandardCharsets.UTF_8));
    assertEquals("2f4912aee74c910f8b401e2ca92f97c1aca154ed4f638455a419f3eff6f26c7a", Sha256.digest(input, 64));
    assertThrows(java.io.IOException.class, () -> Sha256.digest(
        new ByteArrayInputStream(new byte[] {1, 2, 3}), 2));
  }
}
