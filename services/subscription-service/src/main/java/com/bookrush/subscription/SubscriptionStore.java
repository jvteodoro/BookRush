package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static com.bookrush.subscription.CommercialPolicy.*;

@Service
class SubscriptionStore {
  private static final Contract FREE = new Contract("FREE", 1, State.FREE, null, null, 0, false, false, 0);
  private static final String SELECT = """
      SELECT c.*, p.publish_books, p.premium_family, p.additional_seats
      FROM subscription.contract c JOIN subscription.plan p
        ON (p.code, p.version) = (c.plan_code, c.plan_version)
      WHERE c.identity_issuer = ? AND c.identity_subject = ?
      """;

  record BillingEvent(UUID eventId, long sourceVersion, Operation operation, Instant startsAt, Instant endsAt) {}
  record Snapshot(String plan, int planVersion, State state, Instant effectiveUntil, long version,
                  boolean publishBooks, boolean premiumFamily, int additionalSeats) {}

  private final JdbcTemplate db;
  private final Clock clock;
  private final ObjectMapper json;
  private final MeterRegistry metrics;

  SubscriptionStore(JdbcTemplate db, Clock clock, ObjectMapper json, MeterRegistry metrics) {
    this.db = db;
    this.clock = clock;
    this.json = json;
    this.metrics = metrics;
  }

  Snapshot snapshot(TechnicalIdentity owner) {
    return snapshot(load(owner, false), clock.instant());
  }

  Contract contract(TechnicalIdentity owner) {
    return load(owner, false);
  }

  private Snapshot snapshot(Contract c, Instant now) {
    boolean paid = c.paid(now);
    return new Snapshot(c.plan(), c.planVersion(), c.effectiveState(now), paid ? c.endsAt() : null,
        c.version(), paid && c.publishBooks(), paid && c.premiumFamily(), paid ? c.seats() : 0);
  }

  @Transactional
  long apply(TechnicalIdentity owner, TechnicalIdentity agent, BillingEvent event) {
    db.update("""
        INSERT INTO subscription.contract(identity_issuer, identity_subject, plan_code, plan_version, state)
        VALUES (?, ?, 'FREE', 1, 'FREE') ON CONFLICT DO NOTHING
        """, owner.issuer(), owner.subject());
    Contract current = load(owner, true);
    String payload = encode(event);
    var replay = db.queryForList("""
        SELECT source_version FROM subscription.transition
        WHERE event_id = ? AND identity_issuer = ? AND identity_subject = ? AND command = ?::jsonb
        """, Long.class, event.eventId(), owner.issuer(), owner.subject(), payload);
    if (!replay.isEmpty()) {
      return replay.getFirst();
    }
    if (event.sourceVersion() - 1 != current.version()) {
      throw conflict("expected next source version");
    }
    Instant now = clock.instant();
    State next = CommercialPolicy.next(current, event.operation(), now, event.startsAt(), event.endsAt());
    int planVersion = event.operation() == Operation.ACTIVATE
        ? db.queryForObject("SELECT max(version) FROM subscription.plan WHERE code = 'PREMIUM'", Integer.class)
        : current.planVersion();
    Instant start = event.startsAt() == null ? current.startsAt() : event.startsAt();
    Instant end = event.endsAt() == null ? current.endsAt() : event.endsAt();
    try {
      db.update("""
          INSERT INTO subscription.transition(event_id, identity_issuer, identity_subject, source_version,
              agent_issuer, agent_subject, command, previous_state, resulting_state, recorded_at)
          VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
          """, event.eventId(), owner.issuer(), owner.subject(), event.sourceVersion(), agent.issuer(),
          agent.subject(), payload, current.state().name(), next.name(), utc(now));
    } catch (DuplicateKeyException duplicate) {
      throw conflict("event ID already used");
    }
    db.update("""
        UPDATE subscription.contract SET plan_code = 'PREMIUM', plan_version = ?, state = ?,
          starts_at = ?, ends_at = ?, version = ? WHERE identity_issuer = ? AND identity_subject = ?
        """, planVersion, next.name(), utc(start), utc(end), event.sourceVersion(), owner.issuer(), owner.subject());
    Snapshot updated = snapshot(load(owner, false), now);
    db.update("""
        INSERT INTO subscription.outbox(event_id, event_type, payload, created_at)
        VALUES (?, 'EntitlementChanged', ?::jsonb, ?)
        """, event.eventId(), encode(Map.of(
            "eventId", event.eventId(), "schemaVersion", 1, "eventType", "EntitlementChanged",
            "producer", "subscription-service", "occurredAt", now, "aggregateVersion", updated.version(),
            "ownerRef", owner, "entitlements", updated)), utc(now));
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        metrics.counter("bookrush.subscription.transitions", "operation", event.operation().name(),
            "state", next.name()).increment();
      }
    });
    return event.sourceVersion();
  }

  private Contract load(TechnicalIdentity owner, boolean lock) {
    return db.query(SELECT + (lock ? " FOR UPDATE OF c" : ""), (rs, index) -> {
      OffsetDateTime start = rs.getObject("starts_at", OffsetDateTime.class);
      OffsetDateTime end = rs.getObject("ends_at", OffsetDateTime.class);
      return new Contract(rs.getString("plan_code"), rs.getInt("plan_version"),
          State.valueOf(rs.getString("state")), start == null ? null : start.toInstant(),
          end == null ? null : end.toInstant(), rs.getLong("version"), rs.getBoolean("publish_books"),
          rs.getBoolean("premium_family"), rs.getInt("additional_seats"));
    }, owner.issuer(), owner.subject()).stream().findFirst().orElse(FREE);
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("cannot encode subscription event", error);
    }
  }

  private static OffsetDateTime utc(Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  private static ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }
}
