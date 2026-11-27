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
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
  record HouseholdView(UUID id, String role, int additionalSeats, int occupiedOrReserved, List<Member> members) {}
  enum MemberState { ACTIVE, SUSPENDED, REMOVED }
  record Member(UUID id, MemberState state, boolean delegationEnabled, long version, long permissionVersion) {}
  record Accepted(UUID membershipId, UUID householdId) {}
  record MembershipFacts(String state, UUID householdId, UUID membershipId, boolean delegationEnabled,
                         long membershipVersion, long permissionVersion, Instant holderPremiumUntil) {}
  private record MemberRow(Member member, TechnicalIdentity identity) {}

  private static final String MEMBERS = """
      SELECT m.*, COALESCE(p.can_publish, false) AS delegation, COALESCE(p.version, 0) AS permission_version
      FROM family.membership m LEFT JOIN family.member_permission p ON p.membership_id = m.id
      """;

  private static final RowMapper<MemberRow> MEMBER = (r, n) -> new MemberRow(
      new Member(r.getObject("id", UUID.class), MemberState.valueOf(r.getString("state")),
          r.getBoolean("delegation"), r.getLong("version"), r.getLong("permission_version")),
      new TechnicalIdentity(r.getString("identity_issuer"), r.getString("identity_subject")));
  record Invitation(UUID id, UUID householdId, String state, Instant expiresAt) {}

  private final JdbcTemplate db;
  private final Clock clock;
  private final ObjectMapper json;
  private final SubscriptionEntitlements entitlements;
  private final MeterRegistry metrics;

  FamilyStore(JdbcTemplate db, Clock clock, ObjectMapper json, SubscriptionEntitlements entitlements, MeterRegistry metrics) {
    this.db = db;
    this.clock = clock;
    this.json = json;
    this.entitlements = entitlements;
    this.metrics = metrics;
  }

  @Transactional
  Household create(TechnicalIdentity owner) {
    var existing = findHousehold(owner);
    if (!existing.isEmpty()) {
      return new Household(existing.getFirst());
    }
    // Membership writers acquire the same identity lock before any household lock.
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
    var owned = findHousehold(owner);
    if (!owned.isEmpty()) {
      UUID id = owned.getFirst();
      var members = members(owner, id);
      return new HouseholdView(id, "OWNER", ADDITIONAL_SEATS, members.size() + reservationCount(id, now()), members);
    }
    // Members see their household without the holder-only member list.
    var memberOf = db.queryForList("""
        SELECT household_id FROM family.membership
        WHERE identity_issuer = ? AND identity_subject = ? AND state = 'ACTIVE'
        """, UUID.class, owner.issuer(), owner.subject());
    UUID id = memberOf.stream().findFirst().orElseThrow(FamilyStore::notFound);
    int active = db.queryForObject("""
        SELECT count(*) FROM family.membership WHERE household_id = ? AND state IN ('ACTIVE','SUSPENDED')
        """, Integer.class, id);
    return new HouseholdView(id, "MEMBER", ADDITIONAL_SEATS, active + reservationCount(id, now()), List.of());
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

  @Transactional
  Accepted accept(TechnicalIdentity recipient, UUID invitationId) {
    lockIdentity(recipient);
    var invitation = ownInvitation(recipient, invitationId);
    TechnicalIdentity owner = lockHousehold(invitation.householdId());
    invitation = ownInvitation(recipient, invitationId);
    UUID household = invitation.householdId();
    if ("ACCEPTED".equals(invitation.state())) {
      UUID membership = db.queryForObject("SELECT id FROM family.membership WHERE invitation_id = ?",
          UUID.class, invitationId);
      return new Accepted(membership, household);
    }
    ensurePending(invitation);
    if (owner.equals(recipient) || unavailableInHousehold(recipient)) {
      throw FamilyProblem.conflict("FAMILY_MEMBERSHIP_CONFLICT");
    }
    Instant deadline = entitlements.requirePremium(owner);
    ensurePending(invitation);
    entitlements.checkDeadline(deadline);
    var membership = UUID.randomUUID();
    var now = now();
    db.update("""
        INSERT INTO family.membership(id, household_id, identity_issuer, identity_subject,
            state, created_at, updated_at, invitation_id)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
        """, membership, household, recipient.issuer(), recipient.subject(), now, now, invitationId);
    db.update("INSERT INTO family.member_permission(membership_id) VALUES (?)", membership);
    db.update("UPDATE family.invitation SET state = 'ACCEPTED', updated_at = ? WHERE id = ?", now, invitationId);
    emit(household, invitationId, recipient, "InvitationAccepted");
    emit(household, membership, recipient, "MemberAdded");
    ensurePending(invitation);
    entitlements.checkDeadline(deadline);
    return new Accepted(membership, household);
  }

  @Transactional
  void decline(TechnicalIdentity recipient, UUID invitationId) {
    lockIdentity(recipient);
    var invitation = ownInvitation(recipient, invitationId);
    lockHousehold(invitation.householdId());
    invitation = ownInvitation(recipient, invitationId);
    if ("DECLINED".equals(invitation.state())) {
      return;
    }
    ensurePending(invitation);
    db.update("UPDATE family.invitation SET state = 'DECLINED', updated_at = ? WHERE id = ?", now(), invitationId);
    emit(invitation.householdId(), invitationId, recipient, "InvitationDeclined");
  }

  List<Member> members(TechnicalIdentity owner, UUID household) {
    requireOwner(owner, household);
    return db.query(MEMBERS + """
        WHERE m.household_id = ? AND m.state IN ('ACTIVE','SUSPENDED') ORDER BY m.created_at, m.id
        """, MEMBER, household).stream().map(MemberRow::member).toList();
  }

  Member permission(TechnicalIdentity owner, UUID household, UUID member) {
    requireOwner(owner, household);
    return member(household, member);
  }

  @Transactional
  void changeState(TechnicalIdentity owner, UUID household, UUID id, MemberState wanted, long expected) {
    requireOwner(owner, household);
    lockIdentity(memberRow(household, id).identity());
    lockOwned(owner, household);
    Member current = member(household, id);
    verifyVersion(current.version(), expected, current.state() == wanted);
    if (current.state() == wanted) {
      return;
    }
    if (current.state() == MemberState.REMOVED) {
      throw FamilyProblem.conflict("MEMBERSHIP_REMOVED");
    }
    Instant deadline = wanted == MemberState.ACTIVE ? entitlements.requirePremium(owner) : null;
    db.update("UPDATE family.membership SET state = ?, version = version + 1, updated_at = ? WHERE id = ?",
        wanted.name(), now(), id);
    if (wanted == MemberState.REMOVED && current.delegationEnabled()) {
      writePermission(id, false);
      emit(household, id, owner, "PublicationDelegationRevoked");
    }
    emit(household, id, owner, switch (wanted) {
      case ACTIVE -> "MemberReactivated";
      case SUSPENDED -> "MemberSuspended";
      case REMOVED -> "MemberRemoved";
    });
    if (wanted == MemberState.ACTIVE) {
      entitlements.checkDeadline(deadline);
    }
  }

  @Transactional
  Member changePermission(TechnicalIdentity owner, UUID household, UUID id, boolean enabled, long expected) {
    lockOwned(owner, household);
    Member current = member(household, id);
    if (current.state() == MemberState.REMOVED || (enabled && current.state() != MemberState.ACTIVE)) {
      throw FamilyProblem.conflict("MEMBERSHIP_NOT_ACTIVE");
    }
    verifyVersion(current.permissionVersion(), expected, current.delegationEnabled() == enabled);
    if (current.delegationEnabled() == enabled) {
      return current;
    }
    Instant deadline = enabled ? entitlements.requirePremium(owner) : null;
    writePermission(id, enabled);
    emit(household, id, owner, enabled ? "PublicationDelegationGranted" : "PublicationDelegationRevoked");
    if (enabled) {
      entitlements.checkDeadline(deadline);
    }
    return member(household, id);
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  MembershipFacts membershipFacts(TechnicalIdentity identity) {
    var local = localMembership(identity);
    var view = local.facts();
    return new MembershipFacts(view.state(), view.householdId(), view.membershipId(), view.delegationEnabled(),
        view.membershipVersion(), view.permissionVersion(),
        "ACTIVE".equals(view.state()) ? entitlements.premiumUntil(local.holder()) : null);
  }

  record LocalMembership(MembershipFacts facts, TechnicalIdentity holder) {}

  LocalMembership localMembership(TechnicalIdentity identity) {
    return db.query("""
        SELECT m.id, m.household_id, m.state, m.version, h.owner_issuer, h.owner_subject,
               COALESCE(p.can_publish, false) AS delegation, COALESCE(p.version, 0) AS permission_version
        FROM family.membership m JOIN family.household h ON h.id = m.household_id
        LEFT JOIN family.member_permission p ON p.membership_id = m.id
        WHERE m.identity_issuer = ? AND m.identity_subject = ? AND m.state IN ('ACTIVE', 'SUSPENDED')
        """, (row, index) -> new LocalMembership(
            new MembershipFacts(row.getString("state"), row.getObject("household_id", UUID.class),
                row.getObject("id", UUID.class), row.getBoolean("delegation"), row.getLong("version"),
                row.getLong("permission_version"), null),
            new TechnicalIdentity(row.getString("owner_issuer"), row.getString("owner_subject"))),
        identity.issuer(), identity.subject()).stream().findFirst()
        .orElse(new LocalMembership(new MembershipFacts("ABSENT", null, null, false, 0, 0, null), null));
  }

  boolean ownsHousehold(TechnicalIdentity actor, UUID id) {
    return findHousehold(actor).contains(id);
  }

  private void writePermission(UUID member, boolean enabled) {
    db.update("""
        INSERT INTO family.member_permission(membership_id, can_publish, version) VALUES (?, ?, 1)
        ON CONFLICT (membership_id) DO UPDATE SET can_publish = EXCLUDED.can_publish,
          version = family.member_permission.version + 1
        """, member, enabled);
  }

  private static void verifyVersion(long actual, long expected, boolean sameState) {
    if (expected != actual && !(sameState && expected >= 0 && expected == actual - 1)) {
      throw FamilyProblem.conflict("VERSION_CONFLICT");
    }
  }

  private Member member(UUID household, UUID member) {
    return memberRow(household, member).member();
  }

  private MemberRow memberRow(UUID household, UUID member) {
    return db.query(MEMBERS + " WHERE m.household_id = ? AND m.id = ?", MEMBER, household, member)
        .stream().findFirst().orElseThrow(FamilyStore::notFound);
  }

  private Invitation ownInvitation(TechnicalIdentity recipient, UUID id) {
    return db.query(INVITES + " WHERE id = ? AND recipient_issuer = ? AND recipient_subject = ?",
        INVITATION, id, recipient.issuer(), recipient.subject()).stream().findFirst().orElseThrow(FamilyStore::notFound);
  }

  private TechnicalIdentity lockHousehold(UUID household) {
    return db.query("SELECT owner_issuer, owner_subject FROM family.household WHERE id = ? FOR UPDATE",
        (r, n) -> new TechnicalIdentity(r.getString(1), r.getString(2)), household)
        .stream().findFirst().orElseThrow(FamilyStore::notFound);
  }

  private void ensurePending(Invitation invitation) {
    if (!"PENDING".equals(invitation.state()) || !invitation.expiresAt().isAfter(clock.instant())) {
      throw FamilyProblem.conflict("INVITATION_NOT_PENDING");
    }
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
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        metrics.counter("bookrush.family.events", "event", type).increment();
      }
    });
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
