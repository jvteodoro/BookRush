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
    assertEquals(
        "2f4912aee74c910f8b401e2ca92f97c1aca154ed4f638455a419f3eff6f26c7a",
        Sha256.digest(input, 64));
    assertThrows(
        java.io.IOException.class,
        () -> Sha256.digest(new ByteArrayInputStream(new byte[] {1, 2, 3}), 2));
  }
  @Test
void ageCategoryFailsClosed() {
  var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-20T00:00:00Z"), java.time.ZoneOffset.UTC);
  assertEquals(AgeCategory.ADULT, AgeCategory.fromBirthdate("2008-10-20", clock));
  assertEquals(AgeCategory.MINOR, AgeCategory.fromBirthdate("2008-10-21", clock));
  assertEquals(AgeCategory.UNKNOWN, AgeCategory.fromBirthdate(null, clock));
  assertEquals(AgeCategory.UNKNOWN, AgeCategory.fromBirthdate("2999-01-01", clock));
  assertEquals(AgeCategory.UNKNOWN, AgeCategory.fromBirthdate("nope", clock));
  assertFalse(AgeCategory.UNKNOWN.canAccess(18));
  assertTrue(AgeCategory.MINOR.canAccess(0));
}
}
