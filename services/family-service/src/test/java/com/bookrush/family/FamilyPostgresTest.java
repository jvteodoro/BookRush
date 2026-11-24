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

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "FAMILY_TEST_JDBC", matches = ".+")
class FamilyPostgresTest {
  private static final String PRODUCT = "https://test.invalid/realms/bookrush";
  private static final String ROOT = "/api/v1/family";
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final RSAKey KEY = rsaKey();
  private static final AtomicInteger ENTITLEMENT_STATUS = new AtomicInteger(200);
  private static final AtomicInteger ENABLED = new AtomicInteger(1);
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
    Flyway.configure().dataSource(jdbc, "postgres", password).schemas("family").target("1").load().migrate();
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
          && "PREMIUM_FAMILY".equals(body.path("entitlement").asText())
          && PRODUCT.equals(body.path("owner").path("issuer").asText())
          && !body.path("owner").path("subject").asText().isBlank();
      respond(exchange, valid ? ENTITLEMENT_STATUS.get() : 401, ENABLED.get() == 1
          ? "{\"enabled\":true,\"effectiveUntil\":\"2027-01-01T00:00:00Z\",\"version\":1}"
          : "{\"enabled\":false,\"effectiveUntil\":null,\"version\":2}");
    });
    dependencies.start();
    String endpoint = "http://127.0.0.1:" + dependencies.getAddress().getPort();
    props.add("spring.datasource.url", () -> jdbc);
    props.add("spring.datasource.username", () -> "postgres");
    props.add("spring.datasource.password", () -> password);
    props.add("bookrush.security.issuer", () -> PRODUCT);
    props.add("bookrush.security.jwk-set-uri", () -> endpoint + "/jwks");
    props.add("family.subscription-url", () -> endpoint);
    props.add("family.token-url", () -> endpoint + "/token");
    props.add("family.client-secret", () -> "test-only");
    props.add("family.cors-origins", () -> "https://client.test.invalid");
    props.add("family.expiration-delay", () -> 3600000);
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
    ENABLED.set(1);
    ENTITLEMENT_STATUS.set(200);
  }

  @Test
  void validatesCleanMigrationsAndUpgrade() throws Exception {
    assertEquals(2, db.queryForObject("SELECT count(*) FROM family.flyway_schema_history WHERE version IN ('1','2')", Integer.class));
    String jdbc = System.getenv("FAMILY_TEST_JDBC");
    String password = System.getenv("FAMILY_TEST_PASSWORD");
    String database = "family_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(jdbc, "postgres", password);
         var sql = connection.createStatement()) {
      sql.execute("CREATE DATABASE " + database);
      try {
        var flyway = Flyway.configure().dataSource(jdbc.replaceFirst("family_test$", database),
            "postgres", password).schemas("family").load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
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
    var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(owner.subject()).audience(audience)
        .expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("scope", scope).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(KEY));
    return "Bearer " + jwt.serialize();
  }
}
