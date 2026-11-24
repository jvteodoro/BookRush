package com.bookrush.subscription;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static com.bookrush.subscription.CommercialPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class CommercialPolicyTest {
  private final Instant start = Instant.parse("2026-11-24T00:00:00Z");
  private final Instant end = start.plusSeconds(60);
  private final Contract active = new Contract("PREMIUM", 1, State.ACTIVE, start, end, 1, true, true, 5);

  @Test
  void usesAnInclusiveStartAndExclusiveEnd() {
    assertFalse(active.paid(start.minusNanos(1)));
    assertTrue(active.paid(start));
    assertTrue(active.paid(end.minusNanos(1)));
    assertFalse(active.paid(end));
    assertEquals(State.EXPIRED, active.effectiveState(end));
  }

  @Test
  void rejectsInvalidWindowAndRenewalAfterExpiry() {
    var invalid = assertThrows(ResponseStatusException.class,
        () -> next(active, Operation.RENEW, start, start, start));
    assertEquals(422, invalid.getStatusCode().value());
    var expired = assertThrows(ResponseStatusException.class,
        () -> next(active, Operation.RENEW, end, start, end.plusSeconds(60)));
    assertEquals(409, expired.getStatusCode().value());
  }
}
