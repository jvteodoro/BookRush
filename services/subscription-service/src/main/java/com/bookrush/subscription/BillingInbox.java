package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class BillingInbox {
  enum Status { PENDING, APPLIED, STALE }
  record Receipt(UUID eventId, Status state, long sourceVersion, Long currentVersion) {}
  record Event(String billingSource, String externalEventId, TechnicalIdentity owner, long sourceVersion,
               CommercialPolicy.Operation operation, Instant startsAt, Instant endsAt) {
    Event {
      // PostgreSQL stores timestamps to microsecond precision, including for replay comparison.
      startsAt = micros(startsAt);
      endsAt = micros(endsAt);
    }
  }
  private record Stored(UUID eventId, Event event, TechnicalIdentity agent, Status state) {}

  private final JdbcTemplate db;
  private final SubscriptionStore subscriptions;
  private final Clock clock;
  private final TransactionTemplate transaction;
  private final MeterRegistry metrics;

  BillingInbox(JdbcTemplate db, SubscriptionStore subscriptions, Clock clock,
      PlatformTransactionManager manager, MeterRegistry metrics) {
    this.db = db;
    this.subscriptions = subscriptions;
    this.clock = clock;
    this.transaction = new TransactionTemplate(manager);
    this.metrics = metrics;
  }

  Receipt submit(Event event, TechnicalIdentity agent) {
    // First commit records the normalized delivery even when processing cannot proceed.
    transaction.executeWithoutResult(status -> db.update("""
        INSERT INTO subscription.billing_inbox(event_id, billing_source, external_event_id,
          identity_issuer, identity_subject, agent_issuer, agent_subject, source_version,
          operation, starts_at, ends_at, received_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (billing_source, external_event_id) DO NOTHING
        """, UUID.randomUUID(), event.billingSource(), event.externalEventId(),
        event.owner().issuer(), event.owner().subject(), agent.issuer(), agent.subject(),
        event.sourceVersion(), event.operation().name(), utc(event.startsAt()), utc(event.endsAt()),
        utc(clock.instant())));

    try {
      Receipt receipt = Objects.requireNonNull(transaction.execute(status -> process(event)));
      metrics.counter("bookrush.subscription.billing.deliveries", "result", receipt.state().name()).increment();
      return receipt;
    } catch (ResponseStatusException rejected) {
      metrics.counter("bookrush.subscription.billing.deliveries", "result", "CONFLICT").increment();
      throw rejected;
    }
  }

  private Receipt process(Event delivery) {
    Stored stored = db.queryForObject("""
        SELECT * FROM subscription.billing_inbox
        WHERE billing_source = ? AND external_event_id = ? FOR UPDATE
        """, (row, index) -> {
          var owner = new TechnicalIdentity(row.getString("identity_issuer"), row.getString("identity_subject"));
          var agent = new TechnicalIdentity(row.getString("agent_issuer"), row.getString("agent_subject"));
          OffsetDateTime start = row.getObject("starts_at", OffsetDateTime.class);
          OffsetDateTime end = row.getObject("ends_at", OffsetDateTime.class);
          return new Stored(row.getObject("event_id", UUID.class),
              new Event(delivery.billingSource(), delivery.externalEventId(), owner, row.getLong("source_version"),
                  CommercialPolicy.Operation.valueOf(row.getString("operation")),
                  start == null ? null : start.toInstant(), end == null ? null : end.toInstant()),
              agent, Status.valueOf(row.getString("state")));
        }, delivery.billingSource(), delivery.externalEventId());
    if (stored == null || !stored.event().equals(delivery)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "billing event ID reused with different data");
    }
    if (stored.state() != Status.PENDING) {
      return new Receipt(stored.eventId(), stored.state(), delivery.sourceVersion(), null);
    }

    long currentVersion = subscriptions.snapshot(delivery.owner()).version();
    if (delivery.sourceVersion() <= currentVersion) {
      db.update("UPDATE subscription.billing_inbox SET state = 'STALE' WHERE event_id = ?", stored.eventId());
      return new Receipt(stored.eventId(), Status.STALE, delivery.sourceVersion(), null);
    }
    if (delivery.sourceVersion() - 1 > currentVersion) {
      return new Receipt(stored.eventId(), Status.PENDING, delivery.sourceVersion(), currentVersion);
    }

    // Reuse the contract lock and policy. Its write, outbox and inbox acknowledgement commit together.
    subscriptions.apply(delivery.owner(), stored.agent(), new SubscriptionStore.BillingEvent(stored.eventId(),
        delivery.sourceVersion(), delivery.operation(), delivery.startsAt(), delivery.endsAt()));
    db.update("UPDATE subscription.billing_inbox SET state = 'APPLIED', applied_at = ? WHERE event_id = ?",
        utc(clock.instant()), stored.eventId());
    return new Receipt(stored.eventId(), Status.APPLIED, delivery.sourceVersion(), null);
  }

  private static Instant micros(Instant value) {
    return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
  }

  private static OffsetDateTime utc(Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }
}
