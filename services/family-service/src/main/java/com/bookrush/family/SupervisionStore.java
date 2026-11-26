package com.bookrush.family;

import com.bookrush.platform.core.AgePolicy;
import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.core.Sha256;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class SupervisionStore {
  enum State {
    REQUESTED, PENDING_VERIFICATION, ACTIVE, SUSPENDED, REJECTED, REVOKED, EXPIRED;
    boolean terminal() {
      return this == REJECTED || this == REVOKED || this == EXPIRED;
    }
  }
  enum Scope { PRODUCT_ACCESS, BOOKS_ACCESSED, READING_ACTIVITY, COMMENTS }
  enum Action { REVIEW, APPROVE, REJECT, SUSPEND, REVOKE, CONTEST, REDUCE }

  record Grant(UUID id, TechnicalIdentity guardian, TechnicalIdentity minor, State state,
               Set<Scope> requested, Set<Scope> scopes, Instant effectiveFrom, Instant effectiveUntil,
               UUID verificationRef, long version) {
    boolean grants(Scope scope, Instant now) {
      return state == State.ACTIVE && scopes.contains(scope)
          && !now.isBefore(effectiveFrom) && now.isBefore(effectiveUntil);
    }

    View view() {
      return new View(id, minor, state, requested, scopes, effectiveFrom, effectiveUntil, version);
    }
  }
  record View(UUID id, TechnicalIdentity minor, State state, Set<Scope> requestedScopes, Set<Scope> scopes,
              Instant effectiveFrom, Instant effectiveUntil, long version) {}
  record Change(Action action, long expectedVersion, Set<Scope> scopes, Instant effectiveFrom,
                Instant effectiveUntil, UUID verificationRef) {}
  record Receipt(UUID id, State state, long version) {}
  record Facts(boolean allowed, String reason, Instant validUntil, UUID grantId, Long grantVersion,
               Instant windowFrom, Instant windowUntil) {
    static Facts denied(String reason, Instant now) {
      return new Facts(false, reason, now, null, null, null, null);
    }
  }

  private static final Duration WINDOW = Duration.ofDays(90);
  private static final Duration AUDIT_RETENTION = Duration.ofDays(180);
  private static final String SELECT = "SELECT * FROM family.supervision_grant ";
  private static final RowMapper<Grant> GRANT = (row, n) -> new Grant(row.getObject("id", UUID.class),
      new TechnicalIdentity(row.getString("guardian_issuer"), row.getString("guardian_subject")),
      new TechnicalIdentity(row.getString("minor_issuer"), row.getString("minor_subject")),
      State.valueOf(row.getString("state")), decode((String[]) row.getArray("requested_scopes").getArray()),
      decode((String[]) row.getArray("scopes").getArray()),
      instant(row.getObject("effective_from", OffsetDateTime.class)),
      instant(row.getObject("effective_until", OffsetDateTime.class)),
      row.getObject("verification_ref", UUID.class), row.getLong("version"));

  private final JdbcTemplate db;
  private final KeycloakAgeFacts ages;
  private final FamilyStore families;
  private final Clock clock;
  private final ObjectMapper json;
  private final MeterRegistry metrics;

  SupervisionStore(JdbcTemplate db, KeycloakAgeFacts ages, FamilyStore families, Clock clock,
      ObjectMapper json, MeterRegistry metrics) {
    this.db = db;
    this.ages = ages;
    this.families = families;
    this.clock = clock;
    this.json = json;
    this.metrics = metrics;
  }

  @Transactional
  Receipt request(TechnicalIdentity guardian, TechnicalIdentity minor, Set<Scope> scopes, UUID requestId) {
    Grant replay = findRequest(guardian, requestId);
    if (replay != null) {
      return replayRequest(replay, minor, scopes);
    }
    if (!ages.read(guardian).is(AgePolicy.State.ADULT)) {
      throw FamilyProblem.forbidden("AGE_INELIGIBLE");
    }
    if (guardian.equals(minor) || !ages.read(minor).is(AgePolicy.State.MINOR)) {
      throw FamilyProblem.missing("RESOURCE_NOT_FOUND");
    }
    UUID id = UUID.randomUUID();
    OffsetDateTime now = utc(clock.instant());
    int inserted = db.update("""
        INSERT INTO family.supervision_grant(id, guardian_issuer, guardian_subject, minor_issuer, minor_subject,
            request_id, requested_scopes, state, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, string_to_array(?, ','), 'REQUESTED', ?, ?) ON CONFLICT DO NOTHING
        """, id, guardian.issuer(), guardian.subject(), minor.issuer(), minor.subject(), requestId, encode(scopes), now, now);
    if (inserted == 0) {
      replay = findRequest(guardian, requestId);
      if (replay != null) {
        return replayRequest(replay, minor, scopes);
      }
      throw FamilyProblem.conflict("SUPERVISION_REQUEST_ALREADY_EXISTS");
    }
    emit(id, 0, guardian, "SupervisionRequested", false);
    return new Receipt(id, State.REQUESTED, 0);
  }

  List<View> own(TechnicalIdentity guardian) {
    return db.query(SELECT + "WHERE guardian_issuer=? AND guardian_subject=? ORDER BY created_at DESC,id LIMIT 100",
        GRANT, guardian.issuer(), guardian.subject()).stream().map(Grant::view).toList();
  }

  @Transactional
  Receipt change(TechnicalIdentity admin, UUID id, TechnicalIdentity guardian, TechnicalIdentity minor,
      Change command, UUID key) {
    Grant current = lock(id);
    if (!current.guardian().equals(guardian) || !current.minor().equals(minor)) {
      throw FamilyProblem.missing("RESOURCE_NOT_FOUND");
    }
    String details = fingerprint(new Object[]{admin, command.action(), command.expectedVersion(), encode(command.scopes()),
        command.effectiveFrom(), command.effectiveUntil(), command.verificationRef()});
    var replay = db.query("""
        SELECT version, state FROM family.supervision_command WHERE grant_id=? AND request_id=? AND fingerprint=?
        """, (row,n) -> new Receipt(id, State.valueOf(row.getString("state")), row.getLong("version")), id, key, details);
    if (!replay.isEmpty()) {
      return replay.getFirst();
    }
    if (Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM family.supervision_command WHERE grant_id=? AND request_id=?)
        """, Boolean.class, id, key))) {
      throw FamilyProblem.conflict("IDEMPOTENCY_CONFLICT");
    }

    Instant now = clock.instant();
    if (current.version() != command.expectedVersion() || current.state().terminal()
        || (current.effectiveUntil() != null && !now.isBefore(current.effectiveUntil()))) {
      throw FamilyProblem.conflict("SUPERVISION_STATE_CONFLICT");
    }
    State next = next(current, command);
    var scopes = current.scopes();
    Instant from = current.effectiveFrom();
    Instant until = current.effectiveUntil();
    UUID reference = current.verificationRef();
    if (command.action() == Action.APPROVE) {
      if (!ages.read(guardian).is(AgePolicy.State.ADULT)) {
        throw FamilyProblem.forbidden("AGE_INELIGIBLE");
      }
      var target = ages.read(minor);
      if (!target.is(AgePolicy.State.MINOR)) {
        throw FamilyProblem.forbidden("TARGET_NOT_ELIGIBLE");
      }
      now = clock.instant();
      from = command.effectiveFrom().truncatedTo(ChronoUnit.MICROS);
      until = command.effectiveUntil().truncatedTo(ChronoUnit.MICROS);
      if (from.isBefore(now.truncatedTo(ChronoUnit.MICROS)) || !from.isBefore(until) || !now.isBefore(until) || until.isAfter(target.age().adultFrom())
          || (current.effectiveUntil() != null && !now.isBefore(current.effectiveUntil()))) {
        throw FamilyProblem.conflict("SUPERVISION_WINDOW_INVALID");
      }
      scopes = command.scopes();
      reference = command.verificationRef();
    } else if (command.action() == Action.REDUCE) {
      scopes = command.scopes();
    }
    long version = current.version() + 1;
    db.update("""
        UPDATE family.supervision_grant SET state=?, scopes=string_to_array(?, ','), effective_from=?, effective_until=?,
            verification_ref=?, version=?, updated_at=? WHERE id=?
        """, next.name(), encode(scopes), utc(from), utc(until), reference, version, utc(now), id);
    db.update("""
        INSERT INTO family.supervision_command(grant_id, request_id, fingerprint, version, state) VALUES (?,?,?,?,?)
        """, id, key, details, version, next.name());
    emit(id, version, admin, switch (command.action()) {
      case REVIEW -> "SupervisionReviewStarted";
      case APPROVE -> "SupervisionApproved";
      case REJECT -> "SupervisionRejected";
      case REVOKE -> "SupervisionRevoked";
      case SUSPEND -> "SupervisionSuspended";
      case CONTEST -> "SupervisionContested";
      case REDUCE -> "SupervisionScopesReduced";
    }, true);
    return new Receipt(id, next, version);
  }

  private static State next(Grant current, Change command) {
    State state = current.state();
    boolean pending = state == State.REQUESTED || state == State.PENDING_VERIFICATION;
    boolean window = command.effectiveFrom() != null || command.effectiveUntil() != null;
    if (command.action() != Action.APPROVE && (window || command.verificationRef() != null)
        || command.action() != Action.APPROVE && command.action() != Action.REDUCE && !command.scopes().isEmpty()) {
      throw FamilyProblem.conflict("SUPERVISION_COMMAND_INVALID");
    }
    return switch (command.action()) {
      case REVIEW -> require(state == State.REQUESTED, State.PENDING_VERIFICATION);
      case APPROVE -> require((pending || state == State.SUSPENDED || state == State.ACTIVE)
          && command.verificationRef() != null && !command.verificationRef().equals(current.verificationRef())
          && command.effectiveFrom() != null && command.effectiveUntil() != null && !command.scopes().isEmpty()
          && current.requested().containsAll(command.scopes()), State.ACTIVE);
      case REJECT -> require(pending || state == State.SUSPENDED, State.REJECTED);
      case SUSPEND, CONTEST -> require(pending || state == State.ACTIVE, State.SUSPENDED);
      case REDUCE -> require((state == State.ACTIVE || state == State.SUSPENDED)
          && !command.scopes().isEmpty() && current.scopes().containsAll(command.scopes())
          && command.scopes().size() < current.scopes().size(), state);
      case REVOKE -> State.REVOKED;
    };
  }

  @Transactional(readOnly = true)
  Facts product(TechnicalIdentity target) {
    var account = ages.read(target);
    Instant now = clock.instant();
    if (account.is(AgePolicy.State.ADULT)) {
      return new Facts(true, null, now, null, null, null, null);
    }
    if (!account.is(AgePolicy.State.MINOR)) {
      return Facts.denied(account.enabled() ? "ACCOUNT_RESTRICTED" : "ACCOUNT_SUSPENDED", now);
    }
    var membership = families.membershipFacts(target);
    return minorProduct(target, account, membership.holderPremiumUntil());
  }

  Facts minorProduct(TechnicalIdentity target, KeycloakAgeFacts.Account account, Instant holderPremiumUntil) {
    Instant now = clock.instant();
    if (holderPremiumUntil == null || !now.isBefore(holderPremiumUntil)) {
      return Facts.denied("ACCOUNT_RESTRICTED", now);
    }
    var candidates = db.query(SELECT + """
        WHERE minor_issuer=? AND minor_subject=? AND state='ACTIVE'
          AND 'PRODUCT_ACCESS'=ANY(scopes) AND effective_from<=? AND effective_until>?
        ORDER BY effective_until DESC,id
        """, GRANT, target.issuer(), target.subject(), utc(now), utc(now));
    for (Grant candidate : candidates) {
      if (ages.read(candidate.guardian()).is(AgePolicy.State.ADULT)) {
        now = clock.instant();
        Instant until = minimum(candidate.effectiveUntil(), account.age().adultFrom(), holderPremiumUntil);
        if (now.isBefore(until)) {
          return new Facts(true, null, until, candidate.id(), candidate.version(), null, null);
        }
      }
    }
    return Facts.denied("SUPERVISION_REQUIRED", clock.instant());
  }

  @Transactional(readOnly = true)
  Facts scope(TechnicalIdentity guardian, TechnicalIdentity minor, Scope scope) {
    var target = ages.read(minor);
    if (!target.is(AgePolicy.State.MINOR)) {
      return Facts.denied("TARGET_NOT_ELIGIBLE", clock.instant());
    }
    if (!ages.read(guardian).is(AgePolicy.State.ADULT)) {
      return Facts.denied("SUPERVISION_REQUIRED", clock.instant());
    }
    var matching = db.query(SELECT + """
        WHERE guardian_issuer=? AND guardian_subject=? AND minor_issuer=? AND minor_subject=? AND state='ACTIVE'
        """, GRANT, guardian.issuer(), guardian.subject(), minor.issuer(), minor.subject());
    Instant now = clock.instant();
    if (matching.isEmpty() || !matching.getFirst().grants(scope, now) || !now.isBefore(target.age().adultFrom())) {
      return Facts.denied("SUPERVISION_REQUIRED", now);
    }
    var grant = matching.getFirst();
    Instant floor = now.minus(WINDOW);
    return new Facts(true, null, minimum(grant.effectiveUntil(), target.age().adultFrom()),
        grant.id(), grant.version(), grant.effectiveFrom().isAfter(floor) ? grant.effectiveFrom() : floor, now);
  }

  record ReadingWindow(TechnicalIdentity minor, Facts facts) {}

  @Transactional(readOnly = true)
  ReadingWindow readingWindow(TechnicalIdentity guardian, UUID id, Scope scope) {
    var grants = db.query(SELECT + "WHERE id=? AND guardian_issuer=? AND guardian_subject=?",
        GRANT, id, guardian.issuer(), guardian.subject());
    if (grants.isEmpty()) return null;
    var grant = grants.getFirst();
    var facts = scope(guardian, grant.minor(), scope);
    if (facts.allowed() && !id.equals(facts.grantId())) {
      facts = Facts.denied("SUPERVISION_REQUIRED", clock.instant());
    }
    return new ReadingWindow(grant.minor(), facts);
  }

  @Scheduled(fixedDelayString = "${family.supervision-maintenance-delay}",
      initialDelayString = "${family.supervision-maintenance-delay}")
  @Transactional
  void maintain() {
    Instant now = clock.instant();
    db.query(SELECT + """
        WHERE state IN ('ACTIVE','SUSPENDED') AND effective_until<=? ORDER BY effective_until,id LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, GRANT, utc(now)).forEach(grant -> {
      long version = grant.version() + 1;
      db.update("UPDATE family.supervision_grant SET state='EXPIRED', version=?, updated_at=? WHERE id=?",
          version, utc(now), grant.id());
      emit(grant.id(), version, null, "SupervisionExpired", false);
    });
    db.update("""
        DELETE FROM family.supervision_audit WHERE event_id IN
          (SELECT event_id FROM family.supervision_audit WHERE occurred_at<=? ORDER BY occurred_at LIMIT 500)
        """, utc(now.minus(AUDIT_RETENTION)));
  }

  private Grant lock(UUID id) {
    return db.query(SELECT + "WHERE id=? FOR UPDATE", GRANT, id).stream().findFirst()
        .orElseThrow(() -> FamilyProblem.missing("RESOURCE_NOT_FOUND"));
  }

  private Grant findRequest(TechnicalIdentity guardian, UUID key) {
    return db.query(SELECT + "WHERE guardian_issuer=? AND guardian_subject=? AND request_id=?",
        GRANT, guardian.issuer(), guardian.subject(), key).stream().findFirst().orElse(null);
  }

  private static Receipt replayRequest(Grant replay, TechnicalIdentity minor, Set<Scope> scopes) {
    if (!replay.minor().equals(minor) || !replay.requested().equals(scopes)) {
      throw FamilyProblem.conflict("IDEMPOTENCY_CONFLICT");
    }
    return new Receipt(replay.id(), State.REQUESTED, 0);
  }

  private void emit(UUID id, long version, TechnicalIdentity actor, String type, boolean simulated) {
    UUID event = UUID.randomUUID();
    var now = utc(clock.instant());
    db.update("""
        INSERT INTO family.supervision_audit(event_id, grant_id, actor_issuer, actor_subject, event_type,
            version, simulated, occurred_at) VALUES (?,?,?,?,?,?,?,?)
        """, event, id, actor == null ? null : actor.issuer(), actor == null ? null : actor.subject(),
        type, version, simulated, now);
    String payload = serialize(Map.of("eventId", event, "eventType", type, "aggregateId", id,
        "aggregateVersion", version, "schemaVersion", 1, "simulated", simulated, "occurredAt", now));
    db.update("INSERT INTO family.supervision_outbox(event_id,event_type,payload,created_at) VALUES (?,?,?::jsonb,?)",
        event, type, payload, now);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        metrics.counter("bookrush.family.supervision.events", "event", type).increment();
      }
    });
  }

  private static State require(boolean accepted, State next) {
    if (!accepted) {
      throw FamilyProblem.conflict("SUPERVISION_STATE_CONFLICT");
    }
    return next;
  }

  private static Set<Scope> decode(String[] names) {
    return Arrays.stream(names).map(Scope::valueOf).collect(Collectors.toUnmodifiableSet());
  }

  private static String encode(Set<Scope> scopes) {
    return scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }

  private String fingerprint(Object value) {
    byte[] bytes = serialize(value).getBytes(StandardCharsets.UTF_8);
    try {
      return Sha256.digest(new ByteArrayInputStream(bytes), bytes.length);
    } catch (IOException failure) {
      throw new IllegalStateException("cannot fingerprint supervision command", failure);
    }
  }

  private String serialize(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("cannot encode supervision event", failure);
    }
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime utc(Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  private static Instant minimum(Instant... values) {
    return Arrays.stream(values).min(Instant::compareTo).orElseThrow();
  }
}
