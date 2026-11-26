package com.bookrush.platform.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgePolicyTest {
  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  @Test
  void rejectsMissingInvalidAndFutureDates() {
    var clock = at("2026-11-24T03:00:00Z");
    assertEquals(AgePolicy.State.MISSING, AgePolicy.classify(List.of(), AgePolicy.Provenance.NONE, clock).state());
    for (String input : List.of("2026-11-25", "2008-02-30", "2008-2-01", "0000-01-01", " 2008-01-01")) {
      assertEquals(AgePolicy.State.INVALID, AgePolicy.classify(
          List.of(input), AgePolicy.Provenance.DECLARED, clock).state(), input);
    }
    assertEquals(AgePolicy.State.INVALID, AgePolicy.classify(
        List.of("2008-01-01", "2009-01-01"), AgePolicy.Provenance.DECLARED, clock).state());
  }

  @Test
  void calculatesCutoffForOrdinaryAndLeapBirthdays() {
    assertBoundary("2008-11-24", "2026-11-24T03:00:00Z");
    assertBoundary("2008-02-29", "2026-03-01T03:00:00Z");
  }

  private static void assertBoundary(String date, String cutoff) {
    var boundary = Instant.parse(cutoff);
    var before = AgePolicy.classify(List.of(date), AgePolicy.Provenance.DECLARED,
        Clock.fixed(boundary.minusNanos(1), ZoneOffset.UTC));
    var after = AgePolicy.classify(List.of(date), AgePolicy.Provenance.DECLARED, at(cutoff));
    assertEquals(AgePolicy.State.MINOR, before.state());
    assertEquals(AgePolicy.State.ADULT, after.state());
    assertEquals(boundary, before.adultFrom());
  }

  @Test
  void reclassifiesTheSameAuthenticatedSubjectWithoutMintingANewToken() {
    var identityFromOldToken = new TechnicalIdentity("https://id.example/realms/bookrush", "opaque-subject");
    var now = new java.util.concurrent.atomic.AtomicReference<>(at("2026-11-24T02:59:59Z"));
    AgePolicy.Source source = identity -> {
      assertEquals(identityFromOldToken, identity);
      return AgePolicy.classify(List.of("2008-11-24"), AgePolicy.Provenance.DECLARED, now.get());
    };
    assertEquals(AgePolicy.State.MINOR, source.classify(identityFromOldToken).state());
    now.set(at("2026-11-24T03:00:00Z"));
    var adult = source.classify(identityFromOldToken);
    assertEquals(AgePolicy.State.ADULT, adult.state());
    assertTrue(adult.meetsEvidenceRequirement(false));
    assertFalse(adult.meetsEvidenceRequirement(true));
  }
}
