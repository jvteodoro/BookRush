package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
@Conditional(MockBillingEnabled.class)
class MockBillingProvider implements BillingProvider {
  enum Scenario { APPROVE, DECLINE, RENEW, CANCEL, REFUND, CHARGEBACK }

  private static final Duration PERIOD = Duration.ofDays(30);
  private static final String SOURCE = "mock";
  private final JdbcTemplate db;
  private final SubscriptionStore subscriptions;
  private final Clock clock;

  MockBillingProvider(JdbcTemplate db, SubscriptionStore subscriptions, Clock clock) {
    this.db = db;
    this.subscriptions = subscriptions;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Delivery checkout(TechnicalIdentity owner, UUID requestId) {
    return prepare(owner, requestId, Scenario.APPROVE);
  }

  @Transactional
  public Delivery simulate(TechnicalIdentity owner, UUID requestId, Scenario scenario) {
    return prepare(owner, requestId, scenario);
  }

  private Delivery prepare(TechnicalIdentity owner, UUID requestId, Scenario scenario) {
    db.query("""
        SELECT pg_advisory_xact_lock(hashtextextended(jsonb_build_array(?::text, ?::text)::text, 17))
        """, row -> {}, owner.issuer(), owner.subject());
    var receipts = db.query("""
        SELECT external_event_id, scenario, source_version, operation, starts_at, ends_at
        FROM subscription.mock_billing_request WHERE identity_issuer = ? AND identity_subject = ? AND request_id = ?
        """, (row, index) -> {
          if (!scenario.name().equals(row.getString("scenario"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency key already used");
          }
          var id = row.getObject("external_event_id", UUID.class);
          String action = row.getString("operation");
          return new Delivery(id, action == null ? null : new BillingInbox.Event(SOURCE, id.toString(), owner,
              row.getLong("source_version"), CommercialPolicy.Operation.valueOf(action),
              instant(row.getObject("starts_at", OffsetDateTime.class)), instant(row.getObject("ends_at", OffsetDateTime.class))));
        }, owner.issuer(), owner.subject(), requestId);
    if (!receipts.isEmpty()) {
      return receipts.getFirst();
    }

    UUID id = UUID.randomUUID();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    BillingInbox.Event delivery = scenario == Scenario.DECLINE ? null : event(owner, scenario, id, now);
    db.update("""
        INSERT INTO subscription.mock_billing_request(identity_issuer, identity_subject, request_id,
          external_event_id, scenario, source_version, operation, starts_at, ends_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, owner.issuer(), owner.subject(), requestId, id, scenario.name(),
        delivery == null ? null : delivery.sourceVersion(), delivery == null ? null : delivery.operation().name(),
        delivery == null ? null : utc(delivery.startsAt()), delivery == null ? null : utc(delivery.endsAt()), utc(now));
    return new Delivery(id, delivery);
  }

  private BillingInbox.Event event(TechnicalIdentity owner, Scenario scenario, UUID id, Instant now) {
    var current = subscriptions.contract(owner);
    var operation = switch (scenario) {
      case APPROVE -> CommercialPolicy.Operation.ACTIVATE;
      case RENEW -> CommercialPolicy.Operation.RENEW;
      case CANCEL -> CommercialPolicy.Operation.CANCEL_AT_END;
      case REFUND -> CommercialPolicy.Operation.CANCEL_NOW;
      case CHARGEBACK -> CommercialPolicy.Operation.SUSPEND;
      case DECLINE -> throw new IllegalArgumentException("decline has no commercial transition");
    };
    Instant start = scenario == Scenario.APPROVE ? now : scenario == Scenario.RENEW ? current.startsAt() : null;
    Instant end = scenario == Scenario.APPROVE ? now.plus(PERIOD) : null;
    if (scenario == Scenario.RENEW && current.endsAt() != null) {
      end = current.endsAt().plus(PERIOD);
    }
    CommercialPolicy.next(current, operation, now, start, end);
    return new BillingInbox.Event(SOURCE, id.toString(), owner, Math.addExact(current.version(), 1),
        operation, start, end);
  }

  private static Instant instant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }

  private static OffsetDateTime utc(Instant time) {
    return time == null ? null : time.atOffset(ZoneOffset.UTC);
  }
}
