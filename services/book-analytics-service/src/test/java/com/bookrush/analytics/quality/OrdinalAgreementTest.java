package com.bookrush.analytics.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrdinalAgreementTest {
  @Test
  void agreementIsDeterministicAndGroupsByItemAndDimension() {
    var ratings = List.of(
        new OrdinalAgreement.Rating("item-a", "tone", 1),
        new OrdinalAgreement.Rating("item-a", "tone", 5),
        new OrdinalAgreement.Rating("item-a", "tone", 3),
        new OrdinalAgreement.Rating("item-b", "tone", 1));

    assertEquals(0.5, OrdinalAgreement.agreement(ratings), 0.000001);
    assertEquals(0.5, OrdinalAgreement.agreement(List.copyOf(ratings)), 0.000001);
  }

  @Test
  void noComparablePairIsInsufficientRatherThanPerfectAgreement() {
    assertEquals(0.0, OrdinalAgreement.agreement(
        List.of(new OrdinalAgreement.Rating("item-a", "tone", 3))), 0.000001);
    assertEquals(1.0, OrdinalAgreement.agreement(List.of(
        new OrdinalAgreement.Rating("item-a", "tone", 3),
        new OrdinalAgreement.Rating("item-a", "tone", 3))), 0.000001);
  }

  @Test
  void disagreementUsesInclusiveVersionedThreshold() {
    var ratings = List.of(
        new OrdinalAgreement.Rating("item-a", "tone", 1),
        new OrdinalAgreement.Rating("item-a", "tone", 3));
    assertTrue(OrdinalAgreement.disagrees(ratings, 2));
    assertFalse(OrdinalAgreement.disagrees(ratings, 3));
    assertThrows(IllegalArgumentException.class, () -> OrdinalAgreement.disagrees(ratings, 0));
  }
}
