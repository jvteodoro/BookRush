package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"family.supervision-simulation=true", "bookrush.observability.environment=test"})
@org.springframework.test.context.ActiveProfiles("supervision-mock")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "FAMILY_TEST_JDBC", matches = ".+")
class FamilyPostgresTest {
  private static final String PRODUCT = "https://test.invalid/realms/bookrush";
  private static final String PLATFORM = "https://test.invalid/realms/bookrush-platform";
  private static final String TECH_CLIENT = "bookrush-product-access-s2s";
  private static final java.util.concurrent.atomic.AtomicReference<Runnable> DURING_EVALUATION =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final java.util.concurrent.atomic.AtomicReference<Instant> PREMIUM_UNTIL =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final String ROOT = "/api/v1/family";
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final RSAKey KEY = rsaKey();
  private static final AtomicInteger ENTITLEMENT_STATUS = new AtomicInteger(200);
  private static final java.util.concurrent.atomic.AtomicReference<String> ENTITLEMENT_RESPONSE =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final AtomicInteger ENABLED = new AtomicInteger(1);
  private static final java.util.Map<String, String> BIRTHDATES = new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.concurrent.atomic.AtomicReference<Runnable> BEFORE_IDENTITY =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final AtomicInteger AGE_STATUS = new AtomicInteger(200);
  private static final String DECISION = ProductAccessController.PATH;
  private static final String DECISION_WORKLOAD = "fixture-evaluator";
  private static final String SUPERVISION = ROOT + "/supervision";
  private static HttpServer dependencies;
  @Autowired FamilyStore store;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean Clock clock;

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry props) throws Exception {
    String jdbc = System.getenv("FAMILY_TEST_JDBC");
    if (!jdbc.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/family_test")) {
      throw new IllegalArgumentException("Use a disposable local family_test database");
    }
    String password = System.getenv("FAMILY_TEST_PASSWORD");
    Flyway.configure().dataSource(jdbc, "postgres", password).schemas("family").target("3").load().migrate();
    dependencies = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    dependencies.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(KEY.toPublicJWK()).toString()));
    dependencies.createContext("/token", exchange -> {
      String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      boolean valid = "POST".equals(exchange.getRequestMethod())
          && "Basic Ym9va3J1c2gtZmFtaWx5LXMyczp0ZXN0LW9ubHk=".equals(exchange.getRequestHeaders().getFirst("Authorization"))
          && form.contains("grant_type=client_credentials") && form.contains("scope=entitlements.evaluate");
      respond(exchange, valid ? 200 : 401, "{\"access_token\":\"fixture-token\",\"expires_in\":60}");
    });
    dependencies.createContext("/api/internal/v1/subscription/entitlements/evaluate", exchange -> {
      var body = new ObjectMapper().readTree(exchange.getRequestBody());
      boolean valid = "POST".equals(exchange.getRequestMethod())
          && "Bearer fixture-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
          && Set.of("PREMIUM_FAMILY", "PUBLISH_BOOKS").contains(body.path("entitlement").asText())
          && PRODUCT.equals(body.path("owner").path("issuer").asText())
          && !body.path("owner").path("subject").asText().isBlank();
      Runnable hook = DURING_EVALUATION.getAndSet(null);
      if (hook != null) {
        hook.run();
      }
      String override = ENTITLEMENT_RESPONSE.get();
      respond(exchange, valid ? ENTITLEMENT_STATUS.get() : 401, override != null ? override : ENABLED.get() == 1
          ? "{\"enabled\":true,\"effectiveUntil\":\"%s\",\"version\":1}".formatted(PREMIUM_UNTIL.get())
          : "{\"enabled\":false,\"effectiveUntil\":null,\"version\":2}");
    });
    dependencies.createContext("/identity-token", exchange ->
        respond(exchange, 200, "{\"access_token\":\"age-fixture\",\"expires_in\":60}"));
    dependencies.createContext("/identity/users/", exchange -> {
      String subject = exchange.getRequestURI().getPath().substring("/identity/users/".length());
      String date = BIRTHDATES.get(subject);
      Runnable hook = BEFORE_IDENTITY.getAndSet(null);
      if (hook != null) hook.run();
      respond(exchange, !"Bearer age-fixture".equals(exchange.getRequestHeaders().getFirst("Authorization"))
          ? 401 : date == null ? 404 : AGE_STATUS.get(), new ObjectMapper().writeValueAsString(
          java.util.Map.of("id", subject, "enabled", true, "firstName", "Fixture",
              "attributes", java.util.Map.of("birthdate", List.of(date == null ? "" : date)))));
    });
    dependencies.start();
    String endpoint = "http://127.0.0.1:" + dependencies.getAddress().getPort();
    props.add("spring.datasource.url", () -> jdbc);
    props.add("spring.datasource.username", () -> "postgres");
    props.add("spring.datasource.password", () -> password);
    props.add("bookrush.security.issuer", () -> PRODUCT);
    props.add("bookrush.security.jwk-set-uri", () -> endpoint + "/jwks");
    props.add("family.workload-issuer", () -> PLATFORM);
    props.add("family.workload-jwks", () -> endpoint + "/jwks");
    props.add("family.identity-admin-users", () -> endpoint + "/identity/users");
    props.add("family.identity-token-url", () -> endpoint + "/identity-token");
    props.add("family.identity-secret", () -> "age-test-only");
    props.add("family.supervision-maintenance-delay", () -> 3600000);
    props.add("family.subscription-url", () -> endpoint);
    props.add("family.token-url", () -> endpoint + "/token");
    props.add("family.client-secret", () -> "test-only");
    props.add("family.cors-origins", () -> "https://client.test.invalid");
    props.add("family.expiration-delay", () -> 3600000);
    props.add("family.product-access-workloads", () -> DECISION_WORKLOAD
        + "=SESSION_VIEW:SESSION,INVITE_SELF:INVITATION,PRODUCT_USE:BOOK,FAMILY_MANAGE:FAMILY,"
        + "PUBLISH_READ:SUBMISSION,PUBLISH_WRITE:SUBMISSION,PUBLISH_WITHDRAW:SUBMISSION,"
        + "SUPERVISE_HISTORY:READING_HISTORY,SUPERVISE_COMMENTS:COMMENT;fixture-session=SESSION_VIEW:SESSION");
  }

  @AfterAll
  static void cleanup() {
    if (dependencies != null) {
      dependencies.stop(0);
    }
  }

  @BeforeEach
  void setDefaults() {
    when(clock.instant()).thenReturn(NOW);
    when(clock.withZone(org.mockito.ArgumentMatchers.any(java.time.ZoneId.class)))
        .thenAnswer(call -> Clock.fixed(clock.instant(), call.getArgument(0)));
    ENABLED.set(1);
    AGE_STATUS.set(200);
    BEFORE_IDENTITY.set(null);
    BIRTHDATES.clear();
    ENTITLEMENT_STATUS.set(200);
    ENTITLEMENT_RESPONSE.set(null);
    DURING_EVALUATION.set(null);
    PREMIUM_UNTIL.set(Instant.parse("2027-01-01T00:00:00Z"));
  }

  @Test
  void canonicalDecisionEnforcesWireContractAndTechnicalBoundary() throws Exception {
    var reader = identity();
    BIRTHDATES.put(reader.subject(), "");
    String body = decisionBody(reader, reader, "SESSION_VIEW", "SESSION", null);
    String caller = token(identity(), "product-access.evaluate", PLATFORM, "bookrush-family", DECISION_WORKLOAD);
    canonical(null, body, 401).andExpect(jsonPath("$.code").value("INVALID_AUTHENTICATION"));
    canonical(token(reader, "product-access.evaluate"), body, 401);
    canonical(token(reader, "product-access.evaluate", PLATFORM, "wrong", DECISION_WORKLOAD), body, 401);
    canonical(token(reader, "product-access.evaluate", PRODUCT, "bookrush-family", DECISION_WORKLOAD), body, 401);
    canonical(token(reader, "supervision.evaluate", PLATFORM, "bookrush-family", DECISION_WORKLOAD), body, 403)
        .andExpect(jsonPath("$.code").value("CALLER_FORBIDDEN"));
    canonical(token(reader, "product-access.evaluate", PLATFORM, "bookrush-family", "fixture-unlisted"), body, 403);
    canonical(token(reader, "product-access.evaluate", PLATFORM, "bookrush-family", "fixture-session"),
        decisionBody(reader, reader, "INVITE_SELF", "INVITATION", null), 403);
    canonical(caller, body, 200).andExpect(jsonPath("$.allowed").value(true))
        .andExpect(jsonPath("$.reason").doesNotExist()).andExpect(jsonPath("$.policyVersion").value("1"))
        .andExpect(jsonPath("$.correlationId").value("fixture-correlation"))
        .andExpect(jsonPath("$.factVersions").isMap()).andExpect(jsonPath("$.validUntil").value(NOW.plusSeconds(5).toString()));
    canonical(caller, body.replace("\"correlationId\"", "\"unknown\""), 400)
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    canonical(caller, body.replace("\"fixture-correlation\"", "123"), 400);
    canonical(caller, body + "{}", 400);
    canonical(caller, decisionBody(reader, identity(), "INVITE_SELF", "INVITATION", null), 200)
        .andExpect(jsonPath("$.reason").value("OWNERSHIP_REQUIRED"));
    canonical(caller, decisionBody(reader, reader, "SUPERVISE_HISTORY", "READING_HISTORY", "invalid"), 400);
    canonical(caller, decisionBody(reader, reader, "PRODUCT_USE", "BOOK", null), 200)
        .andExpect(jsonPath("$.reason").value("ACCOUNT_RESTRICTED"));
    BIRTHDATES.put(reader.subject(), "1994-01-01");
    canonical(caller, decisionBody(reader, identity(), "PRODUCT_USE", "BOOK", "private"), 200)
        .andExpect(jsonPath("$.reason").value("OWNERSHIP_REQUIRED"));
    AGE_STATUS.set(503);
    canonical(caller, body, 503).andExpect(jsonPath("$.code").value("DECISION_UNAVAILABLE"));
  }

  @Test
  void canonicalPublicationAndCommercialManagementReadCurrentState() throws Exception {
    var holder = identity();
    BIRTHDATES.put(holder.subject(), "1990-01-01");
    String caller = token(identity(), "product-access.evaluate", PLATFORM, "bookrush-family", DECISION_WORKLOAD);
    var house = store.create(holder);
    var deadline = NOW.plusSeconds(2);
    PREMIUM_UNTIL.set(deadline);
    for (String action : List.of("PUBLISH_READ", "PUBLISH_WRITE", "PUBLISH_WITHDRAW")) {
      canonical(caller, decisionBody(holder, holder, action, "SUBMISSION", "fixture"), 200)
          .andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.factVersions.subscription").value("1"))
          .andExpect(jsonPath("$.validUntil").value(deadline.toString()));
    }
    canonical(caller, decisionBody(holder, identity(), "PUBLISH_WRITE", "SUBMISSION", "fixture"), 200)
        .andExpect(jsonPath("$.reason").value("OWNERSHIP_REQUIRED"));
    when(clock.instant()).thenReturn(deadline);
    canonical(caller, decisionBody(holder, holder, "PUBLISH_WRITE", "SUBMISSION", "fixture"), 200)
        .andExpect(jsonPath("$.reason").value("ENTITLEMENT_REQUIRED"));
    canonical(caller, decisionBody(holder, holder, "FAMILY_MANAGE", "FAMILY", "READ:" + house.id()), 200)
        .andExpect(jsonPath("$.allowed").value(true));
    canonical(caller, decisionBody(holder, holder, "FAMILY_MANAGE", "FAMILY", "INVITE:" + house.id()), 200)
        .andExpect(jsonPath("$.reason").value("ENTITLEMENT_REQUIRED"));
    canonical(caller, decisionBody(holder, holder, "FAMILY_MANAGE", "FAMILY", "READ:" + UUID.randomUUID()), 200)
        .andExpect(jsonPath("$.reason").value("OWNERSHIP_REQUIRED"));
    ENTITLEMENT_RESPONSE.set("""
        {"enabled":"true","effectiveUntil":"2027-01-01T00:00:00Z","version":1}
        """);
    canonical(caller, decisionBody(holder, holder, "PUBLISH_WRITE", "SUBMISSION", "fixture"), 503);
    ENTITLEMENT_STATUS.set(503);
    canonical(caller, decisionBody(holder, holder, "PUBLISH_WRITE", "SUBMISSION", "fixture"), 503);
  }

  private org.springframework.test.web.servlet.ResultActions canonical(String credential, String body, int status)
      throws Exception {
    var request = post(DECISION).contentType("application/json").header("X-Correlation-ID", "fixture-correlation")
        .content(body);
    if (credential != null) request.header("Authorization", credential);
    return mvc.perform(request).andExpect(status().is(status));
  }

  private String decisionBody(TechnicalIdentity actor, TechnicalIdentity owner, String action, String type, String id)
      throws Exception {
    var body = json.createObjectNode();
    body.set("actor", json.valueToTree(actor));
    body.put("action", action).put("correlationId", "fixture-correlation");
    var resource = body.putObject("resource").put("type", type);
    if (id != null) resource.put("id", id);
    if (owner != null) resource.set("owner", json.valueToTree(owner));
    body.putObject("authentication").put("clientId", "fixture-web").put("audience", "bookrush-family").putArray("scopes");
    return json.writeValueAsString(body);
  }

  @Test
  void validatesCleanMigrationsAndUpgrade() throws Exception {
    assertEquals(4, db.queryForObject("SELECT count(*) FROM family.flyway_schema_history WHERE version IN ('1','2','3','4')", Integer.class));
    String jdbc = System.getenv("FAMILY_TEST_JDBC");
    String password = System.getenv("FAMILY_TEST_PASSWORD");
    String database = "family_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(jdbc, "postgres", password);
         var sql = connection.createStatement()) {
      sql.execute("CREATE DATABASE " + database);
      try {
        var flyway = Flyway.configure().dataSource(jdbc.replaceFirst("family_test$", database),
            "postgres", password).schemas("family").load();
        assertEquals(4, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
      } finally {
        sql.execute("DROP DATABASE " + database);
      }
    }
  }

  @Test
  void premiumCreatesIdempotentlyAndHolderDoesNotOccupySeat() throws Exception {
    var owner = identity();
    var bearer = token(owner, "family.manage");
    var response = mvc.perform(post(ROOT).header("Authorization", bearer))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    mvc.perform(post(ROOT).header("Authorization", bearer)).andExpect(content().json(response));
    mvc.perform(get(ROOT + "/me").header("Authorization", bearer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.additionalSeats").value(5)).andExpect(jsonPath("$.occupiedOrReserved").value(0));
    ENABLED.set(0);
    mvc.perform(post(ROOT).header("Authorization", token(identity(), "family.manage")))
        .andExpect(status().isForbidden());
  }

  @Test
  void fiveReservationsThenCancelAndExpireReleaseSeats() {
    var owner = identity();
    var household = store.create(owner).id();
    var first = invite(owner, household);
    for (int n = 1; n < 5; n++) {
      invite(owner, household);
    }
    assertEquals(5, store.mine(owner).occupiedOrReserved());
    assertEquals(409, assertThrows(FamilyProblem.class, () -> invite(owner, household)).getStatusCode().value());
    store.cancel(owner, household, first.id());
    store.cancel(owner, household, first.id());
    assertEquals(4, store.mine(owner).occupiedOrReserved());
    invite(owner, household);
    when(clock.instant()).thenReturn(NOW.plusSeconds(7 * 86400));
    assertEquals(0, store.mine(owner).occupiedOrReserved());
    assertTrue(store.sent(owner, household).stream().noneMatch(i -> i.state().equals("PENDING")));
    store.expirePending();
    store.expirePending();
    assertEquals(5, events(household, "InvitationExpired"));
    assertEquals(1, events(household, "InvitationCanceled"));
    invite(owner, household);
  }

  @Test
  void serializesCompetingReservationsWithFourExistingMembers() throws Exception {
    var owner = identity();
    var household = store.create(owner).id();
    for (int n = 0; n < 4; n++) {
      db.update("""
          INSERT INTO family.membership(id, household_id, identity_issuer, identity_subject, state, created_at, updated_at)
          VALUES (?, ?, ?, ?, 'ACTIVE', now(), now())
          """, UUID.randomUUID(), household, PRODUCT, identity().subject());
    }
    var ready = new java.util.concurrent.CyclicBarrier(2);
    Callable<Integer> reserve = () -> {
      var code = UUID.fromString(store.address(identity()).code());
      ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
      try {
        store.invite(owner, household, UUID.randomUUID(), code);
        return 201;
      } catch (FamilyProblem problem) {
        return problem.getStatusCode().value();
      }
    };
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(List.of(reserve, reserve));
      assertEquals(Set.of(201, 409), Set.of(results.getFirst().get(), results.getLast().get()));
    }
    assertEquals(5, store.mine(owner).occupiedOrReserved());
    assertEquals(1, events(household, "InvitationIssued"));
  }

  @Test
  void protectsLookupAndRecipientOwnership() throws Exception {
    var owner = identity();
    var recipient = identity();
    var third = identity();
    var household = store.create(owner).id();
    var address = store.address(recipient);
    var key = UUID.randomUUID();
    var code = UUID.fromString(address.code());
    var invitation = store.invite(owner, household, key, code);
    assertEquals(invitation, store.invite(owner, household, key, code));
    assertEquals(1, events(household, "InvitationIssued"));
    assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(FamilyProblem.class,
        () -> store.invite(owner, household, key, UUID.randomUUID())).getReason());
    assertEquals("INVITATION_ALREADY_PENDING", assertThrows(FamilyProblem.class,
        () -> store.invite(owner, household, UUID.randomUUID(), code)).getReason());
    var ownAddress = UUID.fromString(store.address(owner).code());
    assertEquals(assertThrows(FamilyProblem.class,
        () -> store.invite(owner, household, UUID.randomUUID(), ownAddress)).getReason(),
        assertThrows(FamilyProblem.class,
        () -> store.invite(owner, household, UUID.randomUUID(), UUID.randomUUID())).getReason());
    mvc.perform(get(ROOT + "/invitations/me").header("Authorization", token(recipient, "family.invites")))
        .andExpect(jsonPath("$[0].id").value(invitation.id().toString()))
        .andExpect(jsonPath("$[0].recipient_subject").doesNotExist());
    mvc.perform(get(ROOT + "/invitations/me").header("Authorization", token(third, "family.invites")))
        .andExpect(content().json("[]"));
    mvc.perform(get(ROOT + "/" + household + "/invitations").header("Authorization", token(third, "family.manage")))
        .andExpect(status().isNotFound());
    mvc.perform(delete(ROOT + "/" + household + "/invitations/" + invitation.id())
        .header("Authorization", token(third, "family.manage"))).andExpect(status().isNotFound());
    assertEquals(1, store.mine(owner).occupiedOrReserved());
    assertTrue(store.mine(owner).members().isEmpty());
  }

  @Test
  void sameJwtCannotReserveAfterRevocationAndOutageFailsClosed() throws Exception {
    var owner = identity();
    var bearer = token(owner, "family.manage");
    var household = store.create(owner).id();
    var address = store.address(identity());
    var body = json.writeValueAsString(new FamilyController.InvitationRequest(UUID.fromString(address.code())));
    var request = post(ROOT + "/" + household + "/invitations").contentType("application/json").content(body)
        .header("Authorization", bearer).header("Idempotency-Key", UUID.randomUUID());
    ENABLED.set(0);
    mvc.perform(request).andExpect(status().isForbidden());
    ENABLED.set(1);
    ENTITLEMENT_STATUS.set(503);
    mvc.perform(request).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DECISION_UNAVAILABLE"));
    ENTITLEMENT_STATUS.set(200);
    ENTITLEMENT_RESPONSE.set(null);
    assertEquals(0, store.mine(owner).occupiedOrReserved());
    mvc.perform(request).andExpect(status().isCreated());
  }

  @Test
  void schemaPreventsSimultaneousMembershipsAndCountsSuspendedSeats() {
    var owner = identity();
    var first = store.create(owner).id();
    var second = store.create(identity()).id();
    var member = identity();
    String insert = """
        INSERT INTO family.membership(id, household_id, identity_issuer, identity_subject,
            state, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, now(), now())
        """;
    db.update(insert, UUID.randomUUID(), first, member.issuer(), member.subject(), "SUSPENDED");
    assertEquals(1, store.mine(owner).occupiedOrReserved());
    assertThrows(org.springframework.dao.DuplicateKeyException.class,
        () -> db.update(insert, UUID.randomUUID(), second, member.issuer(), member.subject(), "ACTIVE"));
    assertEquals("FAMILY_MEMBERSHIP_CONFLICT",
        assertThrows(FamilyProblem.class, () -> store.create(member)).getReason());
  }

  @Test
  void rejectsInvalidIssuerAudienceAndScope() throws Exception {
    var owner = identity();
    mvc.perform(post(ROOT)).andExpect(status().isUnauthorized());
    mvc.perform(post(ROOT).header("Authorization", token(owner, "family.invites")))
        .andExpect(status().isForbidden());
    mvc.perform(post(ROOT).header("Authorization", token(owner, "family.manage", PRODUCT, "account")))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(ROOT).header("Authorization", token(owner, "family.manage", PRODUCT + "-platform", "bookrush-family")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void supportsOnlyConfiguredCorsAndAppliesInviteScopeToCodeIssuance() throws Exception {
    mvc.perform(options(ROOT + "/invite-address").header("Origin", "https://client.test.invalid")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://client.test.invalid"));
    mvc.perform(options(ROOT + "/invite-address").header("Origin", "https://other.test.invalid")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
    var recipient = identity();
    mvc.perform(post(ROOT + "/invite-address").header("Authorization", token(recipient, "family.invites")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").isString());
    mvc.perform(post(ROOT + "/invite-address").header("Authorization", token(recipient, "family.manage")))
        .andExpect(status().isForbidden());
  }

  @Test
  void recipientAcceptanceUsesExistingSeatAndDeclineIsIdempotent() throws Exception {
    var owner = identity();
    var household = store.create(owner).id();
    var recipient = identity();
    var invite = invitation(owner, household, recipient);
    for (int i = 0; i < 4; i++) {
      invite(owner, household);
    }
    mvc.perform(post(invitationPath(invite, "accept")).header("Authorization", token(identity(), "family.invites")))
        .andExpect(status().isNotFound());
    var request = post(invitationPath(invite, "accept")).header("Authorization", token(recipient, "family.invites"));
    String receipt = mvc.perform(request).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    mvc.perform(request).andExpect(content().json(receipt));
    assertEquals(5, store.mine(owner).occupiedOrReserved());
    assertFalse(store.members(owner, household).getFirst().delegationEnabled());
    assertEquals(1, events(household, "InvitationAccepted"));
    assertEquals(1, events(household, "MemberAdded"));
    assertThrows(FamilyProblem.class, () -> invite(owner, household));
    var other = identity();
    var secondOwner = identity();
    var secondHousehold = store.create(secondOwner).id();
    var decline = invitation(secondOwner, secondHousehold, other);
    var declineRequest = post(invitationPath(decline, "decline")).header("Authorization", token(other, "family.invites"));
    mvc.perform(declineRequest).andExpect(status().isNoContent());
    mvc.perform(declineRequest).andExpect(status().isNoContent());
    assertEquals(0, store.mine(secondOwner).occupiedOrReserved());
    assertEquals(1, events(secondHousehold, "InvitationDeclined"));
  }

  @Test
  void concurrentSameInviteAndDistinctHouseholdsCannotDuplicateMembership() throws Exception {
    var firstOwner = identity();
    var firstHouse = store.create(firstOwner).id();
    var sameRecipient = identity();
    var same = invitation(firstOwner, firstHouse, sameRecipient);
    var duplicated = race(() -> store.accept(sameRecipient, same.id()), () -> store.accept(sameRecipient, same.id()));
    assertEquals(duplicated.getFirst(), duplicated.getLast());
    assertEquals(1, events(firstHouse, "MemberAdded"));
    var secondOwner = identity();
    var secondHouse = store.create(secondOwner).id();
    var candidate = identity();
    var a = invitation(firstOwner, firstHouse, candidate);
    var b = invitation(secondOwner, secondHouse, candidate);
    var statuses = race(() -> result(() -> store.accept(candidate, a.id())),
                        () -> result(() -> store.accept(candidate, b.id())));
    assertEquals(Set.of(200, 409), Set.copyOf(statuses));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM family.membership WHERE identity_subject = ?",
        Integer.class, candidate.subject()));
  }

  @Test
  void cancelRaceAndExpiredDeadlinesCannotConsumeExtraSeats() throws Exception {
    var owner = identity();
    var household = store.create(owner).id();
    var recipient = identity();
    var invite = invitation(owner, household, recipient);
    var statuses = race(() -> result(() -> store.accept(recipient, invite.id())),
        () -> result(() -> store.cancel(owner, household, invite.id())));
    assertEquals(Set.of(200, 409), Set.copyOf(statuses));
    assertEquals(1, events(household, "InvitationAccepted") + events(household, "InvitationCanceled"));

    var candidate = identity();
    var expiring = invitation(owner, household, candidate);
    when(clock.instant()).thenReturn(expiring.expiresAt());
    assertEquals(409, result(() -> store.accept(candidate, expiring.id())));
    store.expirePending();
    when(clock.instant()).thenReturn(NOW);
    var canceled = invitation(owner, household, candidate);
    store.cancel(owner, household, canceled.id());
    assertEquals(409, result(() -> store.accept(candidate, canceled.id())));
    var premiumRace = invitation(owner, household, candidate);
    var moment = new java.util.concurrent.atomic.AtomicReference<>(NOW);
    when(clock.instant()).thenAnswer(call -> moment.get());
    PREMIUM_UNTIL.set(NOW.plusSeconds(60));
    DURING_EVALUATION.set(() -> moment.set(PREMIUM_UNTIL.get()));
    assertEquals(403, result(() -> store.accept(candidate, premiumRace.id())));
    assertEquals("ABSENT", store.membershipFacts(candidate).state());
  }

  @Test
  void failedS2SAndFailedOutboxDoNotPartiallyAcceptAnInvite() throws Exception {
    var owner = identity();
    var household = store.create(owner).id();
    var candidate = identity();
    var invite = invitation(owner, household, candidate);
    var request = post(invitationPath(invite, "accept")).header("Authorization", token(candidate, "family.invites"));
    ENTITLEMENT_STATUS.set(503);
    mvc.perform(request).andExpect(status().isServiceUnavailable());
    ENTITLEMENT_STATUS.set(200);
    ENTITLEMENT_RESPONSE.set(null);
    ENABLED.set(0);
    mvc.perform(request).andExpect(status().isForbidden());
    ENABLED.set(1);
    db.execute("ALTER TABLE family.outbox RENAME TO outbox_unavailable_test");
    try {
      mvc.perform(request).andExpect(status().isServiceUnavailable());
      assertEquals("ABSENT", store.membershipFacts(candidate).state());
      assertEquals(invite.id(), store.received(candidate).getFirst().id());
    } finally {
      db.execute("ALTER TABLE family.outbox_unavailable_test RENAME TO outbox");
    }
    mvc.perform(request).andExpect(status().isOk());
    assertEquals(1, events(household, "MemberAdded"));
  }

  @Test
  void ownerManagesVersionedStatesAndDelegationWithoutRestoringRemovedMember() throws Exception {
    var owner = identity();
    var household = store.create(owner).id();
    var candidate = identity();
    var invite = invitation(owner, household, candidate);
    var member = store.accept(candidate, invite.id()).membershipId();
    String endpoint = ROOT + "/" + household + "/members/" + member;
    String own = token(owner, "family.manage");
    var grant = put(endpoint + "/publication").contentType("application/json")
        .content("{\"canPublish\":true,\"expectedVersion\":0}");
    mvc.perform(grant.header("Authorization", token(identity(), "family.manage"))).andExpect(status().isNotFound());
    mvc.perform(put(endpoint + "/publication").header("Authorization", own).contentType("application/json")
        .content("{\"canPublish\":true,\"expectedVersion\":0}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.permissionVersion").value(1));
    assertTrue(store.membershipFacts(candidate).delegationEnabled());
    store.changeState(owner, household, member, FamilyStore.MemberState.SUSPENDED, 0);
    assertEquals("SUSPENDED", store.membershipFacts(candidate).state());
    assertEquals(1, store.mine(owner).occupiedOrReserved());
    ENABLED.set(0);
    assertEquals(403, result(() -> store.changeState(owner, household, member, FamilyStore.MemberState.ACTIVE, 1)));
    ENABLED.set(1);
    store.changeState(owner, household, member, FamilyStore.MemberState.ACTIVE, 1);
    assertEquals("ACTIVE", store.membershipFacts(candidate).state());
    store.changePermission(owner, household, member, false, 1);
    store.changePermission(owner, household, member, false, 1);
    assertFalse(store.membershipFacts(candidate).delegationEnabled());
    assertEquals(1, events(household, "PublicationDelegationRevoked"));
    store.changeState(owner, household, member, FamilyStore.MemberState.REMOVED, 2);
    assertEquals(0, store.mine(owner).occupiedOrReserved());
    assertEquals("ABSENT", store.membershipFacts(candidate).state());
    assertEquals(member, store.accept(candidate, invite.id()).membershipId());
    assertEquals("ABSENT", store.membershipFacts(candidate).state());
    assertEquals(409, result(() -> store.changeState(owner, household, member, FamilyStore.MemberState.ACTIVE, 3)));
    var fresh = store.accept(candidate, invitation(owner, household, candidate).id()).membershipId();
    assertNotEquals(member, fresh);
    assertFalse(store.membershipFacts(candidate).delegationEnabled());
    assertEquals(0, db.queryForObject("""
        SELECT count(*) FROM family.supervision_grant WHERE minor_subject = ?
        """, Integer.class, candidate.subject()));
  }

  @Test
  void privateFactsRequirePlatformAudienceScopeAndClientAndNeverGrantPublishing() throws Exception {
    var owner = identity();
    var household = store.create(owner).id();
    var candidate = identity();
    var member = store.accept(candidate, invitation(owner, household, candidate).id()).membershipId();
    store.changePermission(owner, household, member, true, 0);
    String payload = json.writeValueAsString(candidate);
    var endpoint = FamilySecurity.MEMBERSHIP_LOOKUP;
    var technical = new TechnicalIdentity(PLATFORM, "decision");
    String allow = token(technical, "family.membership.read", PLATFORM, "bookrush-family", TECH_CLIENT);
    mvc.perform(post(endpoint).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
    for (String wrong : List.of(token(candidate, "family.membership.read"),
        token(technical, "family.membership.read", PLATFORM, "other", TECH_CLIENT))) {
      mvc.perform(post(endpoint).contentType("application/json").content(payload).header("Authorization", wrong))
          .andExpect(status().isUnauthorized());
    }
    for (String wrong : List.of(token(technical, "wrong", PLATFORM, "bookrush-family", TECH_CLIENT),
        token(technical, "family.membership.read", PLATFORM, "bookrush-family", "other-client"))) {
      mvc.perform(post(endpoint).contentType("application/json").content(payload).header("Authorization", wrong))
          .andExpect(status().isForbidden());
    }
    var request = post(endpoint).contentType("application/json").content(payload).header("Authorization", allow);
    mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"))
        .andExpect(jsonPath("$.delegationEnabled").value(true)).andExpect(jsonPath("$.holderPremiumUntil").exists())
        .andExpect(jsonPath("$.canPublish").doesNotExist()).andExpect(jsonPath("$.age").doesNotExist());
    ENABLED.set(0);
    mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.holderPremiumUntil").doesNotExist());
    ENTITLEMENT_STATUS.set(503);
    mvc.perform(request).andExpect(status().isServiceUnavailable());
    var missing = post(endpoint).contentType("application/json").content(json.writeValueAsString(identity()))
        .header("Authorization", allow);
    mvc.perform(missing).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ABSENT"));
  }

  @Autowired SupervisionStore supervision;

  @Test
  void requestsAreOwnedIdempotentAndDoNotBecomeActive() throws Exception {
    var adult = dated("1995-01-01");
    var minor = dated("2012-01-01");
    var other = dated("1990-11-11");
    String payload = json.writeValueAsString(java.util.Map.of("minor", minor, "scopes", List.of("COMMENTS")));
    var key = UUID.randomUUID();
    var adultToken = token(adult, "supervision.request");
    var request = post(SUPERVISION).contentType("application/json").content(payload)
        .header("Authorization", adultToken).header("Idempotency-Key", key);
    var receipt = mvc.perform(request).andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("REQUESTED"))
        .andReturn().getResponse().getContentAsString();
    mvc.perform(request).andExpect(content().json(receipt));
    mvc.perform(get(SUPERVISION + "/me").header("Authorization", token(other, "supervision.request")))
        .andExpect(content().json("[]"));
    mvc.perform(post(SUPERVISION).contentType("application/json").content(payload)
        .header("Authorization", token(minor, "supervision.request")).header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isForbidden());
    UUID id = UUID.fromString(json.readTree(receipt).get("id").asText());
    assertFalse(supervision.scope(adult, minor, SupervisionStore.Scope.COMMENTS).allowed());
    mvc.perform(command(id, adultToken, adult, minor, "APPROVE", 0, Set.of("COMMENTS"), UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mvc.perform(command(id, supervisorToken("other-service"), adult, minor, "APPROVE", 0, Set.of("COMMENTS"), UUID.randomUUID()))
        .andExpect(status().isForbidden());
    var valid = command(id, supervisorToken("bookrush-supervision-admin-s2s"), adult, minor,
        "APPROVE", 0, Set.of("COMMENTS"), UUID.randomUUID());
    mvc.perform(valid).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
    mvc.perform(valid).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    mvc.perform(get(SUPERVISION + "/me").header("Authorization", adultToken))
        .andExpect(jsonPath("$[0].verificationRef").doesNotExist());
    assertEquals(2, db.queryForObject("SELECT count(*) FROM family.supervision_outbox WHERE payload->>'aggregateId'=?",
        Integer.class, id.toString()));
  }

  @Test
  void productNeedsCommercialEligibilityWhileLegalScopeDoesNot() {
    var payer = dated("1984-01-01");
    var guardian = dated("1987-01-01");
    var minor = dated("2012-01-01");
    var house = store.create(payer).id();
    UUID member = store.accept(minor, invitation(payer, house, minor).id()).membershipId();
    assertTrue(supervision.product(guardian).allowed());
    assertFalse(supervision.product(minor).allowed());
    assertFalse(supervision.scope(payer, minor, SupervisionStore.Scope.COMMENTS).allowed());
    var grant = supervision.request(guardian, minor, Set.of(SupervisionStore.Scope.PRODUCT_ACCESS,
        SupervisionStore.Scope.COMMENTS, SupervisionStore.Scope.BOOKS_ACCESSED), UUID.randomUUID());
    var granted = Set.of(SupervisionStore.Scope.PRODUCT_ACCESS, SupervisionStore.Scope.COMMENTS);
    apply(grant.id(), guardian, minor, SupervisionStore.Action.APPROVE, 0, granted, NOW.plusSeconds(120));
    assertTrue(supervision.product(minor).allowed());
    assertTrue(supervision.scope(guardian, minor, SupervisionStore.Scope.COMMENTS).allowed());
    ENTITLEMENT_STATUS.set(503);
    assertEquals(503, result(() -> supervision.product(minor)));
    assertTrue(supervision.scope(guardian, minor, SupervisionStore.Scope.COMMENTS).allowed());
    ENTITLEMENT_STATUS.set(200);
    assertFalse(supervision.scope(guardian, minor, SupervisionStore.Scope.BOOKS_ACCESSED).allowed());
    assertFalse(supervision.scope(guardian, dated("2010-01-01"), SupervisionStore.Scope.COMMENTS).allowed());
    assertEquals(409, result(() -> apply(grant.id(), guardian, minor, SupervisionStore.Action.REDUCE, 1,
        Set.of(SupervisionStore.Scope.COMMENTS, SupervisionStore.Scope.BOOKS_ACCESSED), null)));
    ENABLED.set(0);
    assertFalse(supervision.product(minor).allowed());
    assertTrue(supervision.scope(guardian, minor, SupervisionStore.Scope.COMMENTS).allowed());
    ENABLED.set(1);
    store.changeState(payer, house, member, FamilyStore.MemberState.SUSPENDED, 0);
    assertFalse(supervision.product(minor).allowed());
    store.changeState(payer, house, member, FamilyStore.MemberState.REMOVED, 1);
    assertFalse(supervision.product(minor).allowed());
    assertTrue(supervision.scope(guardian, minor, SupervisionStore.Scope.COMMENTS).allowed());
    apply(grant.id(), guardian, minor, SupervisionStore.Action.REDUCE, 1, Set.of(SupervisionStore.Scope.PRODUCT_ACCESS), null);
    assertFalse(supervision.scope(guardian, minor, SupervisionStore.Scope.COMMENTS).allowed());
    apply(grant.id(), guardian, minor, SupervisionStore.Action.CONTEST, 2, Set.of(), null);
    assertEquals(SupervisionStore.State.SUSPENDED, supervision.own(guardian).getFirst().state());
    apply(grant.id(), guardian, minor, SupervisionStore.Action.REVOKE, 3, Set.of(), null);
    assertEquals(409, result(() -> apply(grant.id(), guardian, minor, SupervisionStore.Action.APPROVE, 4, granted,
        NOW.plusSeconds(120))));
    assertFalse(supervision.scope(guardian, minor, SupervisionStore.Scope.PRODUCT_ACCESS).allowed());
  }

  @Test
  void ageCutoffAndRevocationReevaluateTheSameTokenAndWindowIsAtMost90Days() throws Exception {
    var guardian = dated("1980-01-01");
    String s2s = token(new TechnicalIdentity(PLATFORM, "consumer"), "supervision.evaluate", PLATFORM,
        "bookrush-family", TECH_CLIENT);
    String canonicalCaller = token(identity(), "product-access.evaluate", PLATFORM, "bookrush-family", DECISION_WORKLOAD);
    for (var fixture : java.util.Map.of("2008-11-24", "2026-11-24T03:00:00Z",
        "2008-02-29", "2026-03-01T03:00:00Z").entrySet()) {
      var target = dated(fixture.getKey());
      var boundary = Instant.parse(fixture.getValue());
      when(clock.instant()).thenReturn(boundary.minus(java.time.Duration.ofDays(120)));
      var request = supervision.request(guardian, target, Set.of(SupervisionStore.Scope.COMMENTS), UUID.randomUUID());
      applyAt(request.id(), guardian, target, SupervisionStore.Action.APPROVE, 0, Set.of(SupervisionStore.Scope.COMMENTS),
          boundary.minus(java.time.Duration.ofDays(120)), boundary);
      when(clock.instant()).thenReturn(boundary.minusSeconds(1));
      var lookup = post(FamilySecurity.SUPERVISION_LOOKUP).contentType("application/json").header("Authorization", s2s)
          .content(json.writeValueAsString(java.util.Map.of("guardian", guardian, "minor", target, "scope", "COMMENTS")));
      mvc.perform(lookup).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
      var facts = supervision.scope(guardian, target, SupervisionStore.Scope.COMMENTS);
      assertEquals(java.time.Duration.ofDays(90), java.time.Duration.between(facts.windowFrom(), facts.windowUntil()));
      assertEquals(boundary, facts.validUntil());
      var canonicalRequest = decisionBody(guardian, target, "SUPERVISE_COMMENTS", "COMMENT", null);
      canonical(canonicalCaller, canonicalRequest, 200)
          .andExpect(jsonPath("$.validUntil").value(boundary.toString()))
          .andExpect(jsonPath("$.factVersions.supervision").value("1"));
      when(clock.instant()).thenReturn(boundary);
      canonical(canonicalCaller, canonicalRequest, 200)
          .andExpect(jsonPath("$.reason").value("TARGET_NOT_ELIGIBLE"));
      mvc.perform(lookup).andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
      assertTrue(supervision.product(target).allowed());
      supervision.maintain();
      assertEquals(SupervisionStore.State.EXPIRED, supervision.own(guardian).stream()
          .filter(g -> g.id().equals(request.id())).findFirst().orElseThrow().state());
    }
    when(clock.instant()).thenReturn(NOW);
    var minor = dated("2010-01-01");
    var grant = supervision.request(guardian, minor, Set.of(SupervisionStore.Scope.COMMENTS), UUID.randomUUID());
    apply(grant.id(), guardian, minor, SupervisionStore.Action.APPROVE, 0, Set.of(SupervisionStore.Scope.COMMENTS),
        NOW.plusSeconds(100));
    var lookup = post(FamilySecurity.SUPERVISION_LOOKUP).contentType("application/json").header("Authorization", s2s)
        .content(json.writeValueAsString(java.util.Map.of("guardian", guardian, "minor", minor, "scope", "COMMENTS")));
    mvc.perform(lookup).andExpect(jsonPath("$.allowed").value(true));
    apply(grant.id(), guardian, minor, SupervisionStore.Action.REVOKE, 1, Set.of(), null);
    mvc.perform(lookup).andExpect(jsonPath("$.allowed").value(false));
  }

  @Test
  void canonicalDecisionInProgressReadsRevocationBeforeGrantLookup() throws Exception {
    var guardian = dated("1983-01-01");
    var minor = dated("2012-01-01");
    var scopes = Set.of(SupervisionStore.Scope.BOOKS_ACCESSED);
    var grant = supervision.request(guardian, minor, scopes, UUID.randomUUID());
    apply(grant.id(), guardian, minor, SupervisionStore.Action.APPROVE, 0, scopes, NOW.plusSeconds(90));
    String caller = token(identity(), "product-access.evaluate", PLATFORM, "bookrush-family", DECISION_WORKLOAD);
    var books = decisionBody(guardian, minor, "SUPERVISE_HISTORY", "READING_HISTORY", "books-accessed");
    canonical(caller, books, 200).andExpect(jsonPath("$.allowed").value(true));
    canonical(caller, decisionBody(guardian, minor, "SUPERVISE_HISTORY", "READING_HISTORY", "reading-activity"), 200)
        .andExpect(jsonPath("$.reason").value("SUPERVISION_REQUIRED"));
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    BEFORE_IDENTITY.set(() -> {
      entered.countDown();
      try {
        if (!release.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("revocation did not finish");
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> canonical(caller, books, 200)
          .andExpect(jsonPath("$.reason").value("SUPERVISION_REQUIRED")));
      try {
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        apply(grant.id(), guardian, minor, SupervisionStore.Action.REVOKE, 1, Set.of(), null);
      } finally {
        release.countDown();
      }
      pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    canonical(caller, books, 200).andExpect(jsonPath("$.reason").value("SUPERVISION_REQUIRED"));
  }

  @Test
  void concurrentApprovalAndRevocationRespectVersionAndTerminalStates() throws Exception {
    var guardian = dated("1981-03-01");
    var minor = dated("2013-01-01");
    var scopes = Set.of(SupervisionStore.Scope.PRODUCT_ACCESS);
    var grant = supervision.request(guardian, minor, scopes, UUID.randomUUID());
    var statuses = race(
        () -> result(() -> apply(grant.id(), guardian, minor, SupervisionStore.Action.APPROVE, 0, scopes, NOW.plusSeconds(200))),
        () -> result(() -> apply(grant.id(), guardian, minor, SupervisionStore.Action.REVOKE, 0, Set.of(), null)));
    assertEquals(Set.of(200, 409), Set.copyOf(statuses));
    var current = supervision.own(guardian).getFirst();
    if (current.state() != SupervisionStore.State.REVOKED) {
      apply(grant.id(), guardian, minor, SupervisionStore.Action.REVOKE, current.version(), Set.of(), null);
    }
    assertEquals(409, result(() -> apply(grant.id(), guardian, minor, SupervisionStore.Action.APPROVE, 2, scopes,
        NOW.plusSeconds(200))));
    var separate = supervision.request(guardian, minor, scopes, UUID.randomUUID());
    apply(separate.id(), guardian, minor, SupervisionStore.Action.REJECT, 0, Set.of(), null);
    assertEquals(SupervisionStore.State.REJECTED, supervision.own(guardian).stream()
        .filter(g -> g.id().equals(separate.id())).findFirst().orElseThrow().state());
  }

  @Test
  void auditRetentionAndFailureRollbackFailClosed() throws Exception {
    var guardian = dated("1988-01-01");
    var minor = dated("2013-01-01");
    var grant = supervision.request(guardian, minor, Set.of(SupervisionStore.Scope.COMMENTS), UUID.randomUUID());
    String actor = supervisorToken("bookrush-supervision-admin-s2s");
    var change = command(grant.id(), actor, guardian, minor, "APPROVE", 0, Set.of("COMMENTS"), UUID.randomUUID());
    AGE_STATUS.set(503);
    mvc.perform(change).andExpect(status().isServiceUnavailable());
    AGE_STATUS.set(200);
    db.execute("ALTER TABLE family.supervision_outbox RENAME TO supervision_outbox_unavailable");
    try {
      mvc.perform(change).andExpect(status().isServiceUnavailable());
      assertEquals(SupervisionStore.State.REQUESTED, supervision.own(guardian).getFirst().state());
    } finally {
      db.execute("ALTER TABLE family.supervision_outbox_unavailable RENAME TO supervision_outbox");
    }
    mvc.perform(change).andExpect(status().isOk());
    db.update("UPDATE family.supervision_audit SET occurred_at=occurred_at-interval '181 days' WHERE grant_id=? AND version=0",
        grant.id());
    supervision.maintain();
    assertEquals(1, db.queryForObject("SELECT count(*) FROM family.supervision_audit WHERE grant_id=?",
        Integer.class, grant.id()));
    assertTrue(db.queryForObject("SELECT simulated FROM family.supervision_audit WHERE grant_id=?",
        Boolean.class, grant.id()));
    assertTrue(supervision.scope(guardian, minor, SupervisionStore.Scope.COMMENTS).allowed());
  }

  private static TechnicalIdentity dated(String birthdate) {
    var identity = identity();
    BIRTHDATES.put(identity.subject(), birthdate);
    return identity;
  }

  private void apply(UUID id, TechnicalIdentity guardian, TechnicalIdentity minor, SupervisionStore.Action action,
      long version, Set<SupervisionStore.Scope> scopes, Instant until) {
    applyAt(id, guardian, minor, action, version, scopes, until == null ? null : NOW, until);
  }

  private void applyAt(UUID id, TechnicalIdentity guardian, TechnicalIdentity minor, SupervisionStore.Action action,
      long version, Set<SupervisionStore.Scope> scopes, Instant from, Instant until) {
    supervision.change(new TechnicalIdentity(PLATFORM, "admin"), id, guardian, minor,
        new SupervisionStore.Change(action, version, scopes, from, until,
            until == null ? null : UUID.randomUUID()), UUID.randomUUID());
  }

  private static String supervisorToken(String client) throws Exception {
    return token(new TechnicalIdentity(PLATFORM, "operator"), "supervision.manage", PLATFORM,
        "bookrush-family-supervision-admin", client);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(
      UUID id, String actor, TechnicalIdentity guardian, TechnicalIdentity minor, String action, long version,
      Set<String> scopes, UUID key) throws Exception {
    return post(FamilySecurity.SUPERVISION_ADMIN + id).contentType("application/json")
        .header("Authorization", actor).header("Idempotency-Key", key)
        .content(json.writeValueAsString(java.util.Map.of("guardian", guardian, "minor", minor, "action", action,
            "expectedVersion", version, "scopes", scopes, "effectiveFrom", NOW,
            "effectiveUntil", NOW.plusSeconds(60), "verificationRef", UUID.randomUUID())));
  }

  private static String invitationPath(FamilyStore.Invitation invite, String action) {
    return ROOT + "/invitations/" + invite.id() + "/" + action;
  }

  private FamilyStore.Invitation invitation(TechnicalIdentity owner, UUID household, TechnicalIdentity recipient) {
    return store.invite(owner, household, UUID.randomUUID(), UUID.fromString(store.address(recipient).code()));
  }

  private static int result(Runnable action) {
    try {
      action.run();
      return 200;
    } catch (FamilyProblem problem) {
      return problem.getStatusCode().value();
    }
  }

  private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(List.<Callable<T>>of(
          () -> { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS); return first.call(); },
          () -> { barrier.await(5, java.util.concurrent.TimeUnit.SECONDS); return second.call(); }));
      return List.of(results.getFirst().get(), results.getLast().get());
    }
  }

  private FamilyStore.Invitation invite(TechnicalIdentity owner, UUID household) {
    return store.invite(owner, household, UUID.randomUUID(), UUID.fromString(store.address(identity()).code()));
  }

  private int events(UUID household, String type) {
    return db.queryForObject("""
        SELECT count(*) FROM family.audit a JOIN family.outbox o ON a.event_id = o.event_id
        WHERE a.household_id = ? AND a.event_type = ?
        """, Integer.class, household, type);
  }

  private static TechnicalIdentity identity() {
    return new TechnicalIdentity(PRODUCT, UUID.randomUUID().toString());
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (var stream = exchange.getResponseBody()) {
      stream.write(body);
    }
  }

  private static RSAKey rsaKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("family-test").generate();
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static String token(TechnicalIdentity owner, String scope) throws Exception {
    return token(owner, scope, PRODUCT, "bookrush-family");
  }

  private static String token(TechnicalIdentity owner, String scope, String issuer, String audience) throws Exception {
    return token(owner, scope, issuer, audience, "fixture-web");
  }

  private static String token(TechnicalIdentity owner, String scope, String issuer, String audience, String client)
      throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(owner.subject()).audience(audience)
        .claim("azp", client).expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("scope", scope).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(KEY));
    return "Bearer " + jwt.serialize();
  }
}
