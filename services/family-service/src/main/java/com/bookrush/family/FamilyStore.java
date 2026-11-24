package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FamilyStore {
  private static final int ADDITIONAL_SEATS = 5;
  private static final Duration INVITE_DURATION = Duration.ofDays(7);
  private static final Duration ADDRESS_DURATION = Duration.ofMinutes(30);
  private static final String INVITES = """
      SELECT id, household_id, state, expires_at FROM family.invitation
      """;
  private static final RowMapper<Invitation> INVITATION = (row, index) -> new Invitation(
      row.getObject("id", UUID.class), row.getObject("household_id", UUID.class),
      row.getString("state"), row.getObject("expires_at", OffsetDateTime.class).toInstant());

  record Address(String code, Instant expiresAt) {}
  record Household(UUID id) {}
  record HouseholdView(UUID id, int additionalSeats, int occupiedOrReserved, List<Member> members) {}
  record Member(UUID id, String state, boolean delegationEnabled) {}
  record Invitation(UUID id, UUID householdId, String state, Instant expiresAt) {}

  private final JdbcTemplate db;
  private final Clock clock;
  private final ObjectMapper json;
  private final SubscriptionEntitlements entitlements;

  FamilyStore(JdbcTemplate db, Clock clock, ObjectMapper json, SubscriptionEntitlements entitlements) {
    this.db = db;
    this.clock = clock;
    this.json = json;
    this.entitlements = entitlements;
  }

  @Transactional
  Household create(TechnicalIdentity owner) {
    var existing = findHousehold(owner);
    if (!existing.isEmpty()) {
      return new Household(existing.getFirst());
    }
    // Future membership writers acquire the same identity lock before any household lock.
    lockIdentity(owner);
    existing = findHousehold(owner);
    if (!existing.isEmpty()) {
      return new Household(existing.getFirst());
    }
    if (unavailableInHousehold(owner)) {
      throw FamilyProblem.conflict("FAMILY_MEMBERSHIP_CONFLICT");
    }
    Instant deadline = entitlements.requirePremium(owner);
    UUID id = UUID.randomUUID();
    entitlements.checkDeadline(deadline);
    int inserted = db.update("""
        INSERT INTO family.household(id, owner_issuer, owner_subject, created_at)
        VALUES (?, ?, ?, ?) ON CONFLICT (owner_issuer, owner_subject) DO NOTHING
        """, id, owner.issuer(), owner.subject(), now());
    if (inserted == 0) {
      return new Household(findHousehold(owner).getFirst());
    }
    emit(id, id, owner, "HouseholdCreated");
    return new Household(id);
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  HouseholdView mine(TechnicalIdentity owner) {
    UUID id = findHousehold(owner).stream().findFirst().orElseThrow(FamilyStore::notFound);
    var members = db.query("""
        SELECT m.id, m.state, COALESCE(p.can_publish, false) AS delegation
        FROM family.membership m LEFT JOIN family.member_permission p ON p.membership_id = m.id
        WHERE m.household_id = ? AND m.state IN ('ACTIVE', 'SUSPENDED') ORDER BY m.created_at, m.id
        """, (r, n) -> new Member(r.getObject("id", UUID.class), r.getString("state"), r.getBoolean("delegation")), id);
    return new HouseholdView(id, ADDITIONAL_SEATS, members.size() + reservationCount(id, now()), members);
  }

  Address address(TechnicalIdentity recipient) {
    String code = UUID.randomUUID().toString();
    Instant expires = clock.instant().plus(ADDRESS_DURATION);
    db.update("""
        INSERT INTO family.invite_address(identity_issuer, identity_subject, code_hash, expires_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (identity_issuer, identity_subject) DO UPDATE
        SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at
        """, recipient.issuer(), recipient.subject(), hash(code), utc(expires));
    return new Address(code, expires);
  }

  @Transactional
  Invitation invite(TechnicalIdentity owner, UUID household, UUID requestId, UUID code) {
    lockOwned(owner, household);
    String codeHash = hash(code.toString());
    OffsetDateTime now = now();
    var replay = db.queryForList("""
        SELECT recipient_code_hash FROM family.invitation WHERE household_id = ? AND request_id = ?
        """, String.class, household, requestId);
    if (!replay.isEmpty()) {
      if (!replay.getFirst().equals(codeHash)) {
        throw FamilyProblem.conflict("IDEMPOTENCY_CONFLICT");
      }
      var original = db.query(INVITES + " WHERE household_id = ? AND request_id = ?",
          INVITATION, household, requestId).getFirst();
      return new Invitation(original.id(), household, "PENDING", original.expiresAt());
    }
    Instant deadline = entitlements.requirePremium(owner);
    now = now();
    TechnicalIdentity recipient = db.query("""
        SELECT identity_issuer, identity_subject FROM family.invite_address
        WHERE code_hash = ? AND expires_at > ?
        """, (r, n) -> new TechnicalIdentity(r.getString(1), r.getString(2)), codeHash, now)
        .stream().findFirst().orElseThrow(FamilyStore::unavailableRecipient);
    if (recipient.equals(owner) || unavailableInHousehold(recipient)) {
      throw unavailableRecipient();
    }
    expireHousehold(household, now);
    boolean pending = Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM family.invitation WHERE household_id = ? AND recipient_issuer = ?
            AND recipient_subject = ? AND state = 'PENDING')
        """, Boolean.class, household, recipient.issuer(), recipient.subject()));
    if (pending) {
      throw FamilyProblem.conflict("INVITATION_ALREADY_PENDING");
    }
    int members = db.queryForObject("""
        SELECT count(*) FROM family.membership WHERE household_id = ? AND state IN ('ACTIVE','SUSPENDED')
        """, Integer.class, household);
    if (members + reservationCount(household, now) >= ADDITIONAL_SEATS) {
      throw FamilyProblem.conflict("NO_FAMILY_SEATS");
    }
    entitlements.checkDeadline(deadline);
    UUID id = UUID.randomUUID();
    Instant expires = now.toInstant().plus(INVITE_DURATION);
    db.update("""
        INSERT INTO family.invitation(id, household_id, recipient_issuer, recipient_subject, state,
            created_at, expires_at, updated_at, request_id, recipient_code_hash)
        VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
        """, id, household, recipient.issuer(), recipient.subject(), now, utc(expires), now, requestId, codeHash);
    emit(household, id, owner, "InvitationIssued");
    return new Invitation(id, household, "PENDING", expires);
  }

  List<Invitation> sent(TechnicalIdentity owner, UUID household) {
    requireOwner(owner, household);
    return db.query("""
        SELECT id, household_id, CASE WHEN state = 'PENDING' AND expires_at <= ? THEN 'EXPIRED' ELSE state END AS state,
            expires_at FROM family.invitation WHERE household_id = ?
        ORDER BY created_at DESC, id DESC LIMIT 100
        """, INVITATION, now(), household);
  }

  List<Invitation> received(TechnicalIdentity recipient) {
    return db.query(INVITES + """
        WHERE recipient_issuer = ? AND recipient_subject = ? AND state = 'PENDING' AND expires_at > ?
        ORDER BY created_at DESC, id DESC LIMIT 100
        """, INVITATION, recipient.issuer(), recipient.subject(), now());
  }

  @Transactional
  void cancel(TechnicalIdentity owner, UUID household, UUID invitation) {
    lockOwned(owner, household);
    var current = db.query(INVITES + " WHERE household_id = ? AND id = ?", INVITATION, household, invitation)
        .stream().findFirst().orElseThrow(FamilyStore::notFound);
    if ("CANCELED".equals(current.state())) {
      return;
    }
    OffsetDateTime now = now();
    if (!"PENDING".equals(current.state()) || !current.expiresAt().isAfter(now.toInstant())) {
      throw FamilyProblem.conflict("INVITATION_NOT_PENDING");
    }
    db.update("UPDATE family.invitation SET state = 'CANCELED', updated_at = ? WHERE id = ?", now, invitation);
    emit(household, invitation, owner, "InvitationCanceled");
  }

  @Scheduled(fixedDelayString = "${family.expiration-delay}", initialDelayString = "${family.expiration-delay}")
  @Transactional
  void expirePending() {
    OffsetDateTime now = now();
    List<UUID> households = db.queryForList("""
        SELECT h.id FROM family.household h WHERE EXISTS (
          SELECT 1 FROM family.invitation i WHERE i.household_id = h.id AND i.state = 'PENDING' AND i.expires_at <= ?
        ) ORDER BY h.id LIMIT 100 FOR UPDATE SKIP LOCKED
        """, UUID.class, now);
    households.forEach(household -> expireHousehold(household, now));
    db.update("""
        DELETE FROM family.invite_address WHERE code_hash IN
          (SELECT code_hash FROM family.invite_address WHERE expires_at <= ? ORDER BY expires_at LIMIT 500)
        """, now);
  }

  private void expireHousehold(UUID household, OffsetDateTime now) {
    List<UUID> expired = db.queryForList("""
        UPDATE family.invitation SET state = 'EXPIRED', updated_at = ?
        WHERE household_id = ? AND state = 'PENDING' AND expires_at <= ? RETURNING id
        """, UUID.class, now, household, now);
    expired.forEach(id -> emit(household, id, null, "InvitationExpired"));
  }

  private boolean unavailableInHousehold(TechnicalIdentity recipient) {
    return Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM family.membership WHERE identity_issuer = ? AND identity_subject = ?
          AND state IN ('ACTIVE', 'SUSPENDED'))
          OR EXISTS(SELECT 1 FROM family.household WHERE owner_issuer = ? AND owner_subject = ?)
        """, Boolean.class, recipient.issuer(), recipient.subject(), recipient.issuer(), recipient.subject()));
  }

  private void lockIdentity(TechnicalIdentity identity) {
    db.query("SELECT pg_advisory_xact_lock(hashtextextended(jsonb_build_array(?::text, ?::text)::text, 0))",
        result -> {}, identity.issuer(), identity.subject());
  }

  private List<UUID> findHousehold(TechnicalIdentity owner) {
    return db.queryForList("SELECT id FROM family.household WHERE owner_issuer = ? AND owner_subject = ?",
        UUID.class, owner.issuer(), owner.subject());
  }

  private void requireOwner(TechnicalIdentity owner, UUID household) {
    owned(owner, household, false);
  }

  private void lockOwned(TechnicalIdentity owner, UUID household) {
    owned(owner, household, true);
  }

  private void owned(TechnicalIdentity owner, UUID household, boolean lock) {
    if (db.queryForList("""
        SELECT id FROM family.household WHERE id = ? AND owner_issuer = ? AND owner_subject = ?
        """ + (lock ? " FOR UPDATE" : ""), UUID.class, household, owner.issuer(), owner.subject()).isEmpty()) {
      throw notFound();
    }
  }

  private int reservationCount(UUID household, OffsetDateTime now) {
    return db.queryForObject("""
        SELECT count(*) FROM family.invitation WHERE household_id = ? AND state = 'PENDING' AND expires_at > ?
        """, Integer.class, household, now);
  }

  private void emit(UUID household, UUID resource, TechnicalIdentity actor, String type) {
    UUID event = UUID.randomUUID();
    long version = db.queryForObject("""
        UPDATE family.household SET event_version = event_version + 1 WHERE id = ? RETURNING event_version
        """, Long.class, household);
    OffsetDateTime now = now();
    db.update("""
        INSERT INTO family.audit(event_id, household_id, resource_id, event_type, actor_issuer, actor_subject, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, event, household, resource, type, actor == null ? null : actor.issuer(),
        actor == null ? null : actor.subject(), now);
    try {
      String payload = json.writeValueAsString(Map.of(
          "eventId", event, "eventType", type, "schemaVersion", 1, "producer", "family-service",
          "occurredAt", now, "aggregateId", household, "aggregateVersion", version,
          "correlationId", event, "payload", Map.of("resourceId", resource)));
      db.update("INSERT INTO family.outbox(event_id, event_type, payload, created_at) VALUES (?, ?, ?::jsonb, ?)",
          event, type, payload, now);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("cannot encode family event", failure);
    }
  }

  private OffsetDateTime now() {
    return utc(clock.instant());
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static String hash(String code) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static FamilyProblem notFound() {
    return FamilyProblem.missing("RESOURCE_NOT_FOUND");
  }

  private static FamilyProblem unavailableRecipient() {
    return FamilyProblem.missing("RECIPIENT_UNAVAILABLE");
  }
}
