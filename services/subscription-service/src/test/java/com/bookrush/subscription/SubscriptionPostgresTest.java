package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import java.time.Clock;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import static com.bookrush.subscription.CommercialPolicy.Operation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"subscription.mock.enabled=true", "bookrush.observability.environment=test"})
@org.springframework.test.context.ActiveProfiles("billing-mock")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "SUBSCRIPTION_TEST_JDBC", matches = ".+")
class SubscriptionPostgresTest {
  private static final String PRODUCT = "https://test.invalid/realms/bookrush";
  private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
  private static final TechnicalIdentity AGENT = new TechnicalIdentity("test-platform", "billing");
  private static final String ME = "/api/v1/subscription/me";
  @Autowired SubscriptionStore store;
  @Autowired BillingInbox billing;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @MockBean Clock clock;
  private static final String PLATFORM = "https://test.invalid/realms/bookrush-platform";
  private static final RSAKey KEY = createKey();
  private static HttpServer jwks;

  private static RSAKey createKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("subscription-test").generate();
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  @AfterAll
  static void stopJwks() {
    if (jwks != null) {
      jwks.stop(0);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) throws Exception {
    String url = System.getenv("SUBSCRIPTION_TEST_JDBC");
    if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/subscription_test")) {
      throw new IllegalArgumentException("This test requires a disposable loopback subscription_test database");
    }
    String password = System.getenv("SUBSCRIPTION_TEST_PASSWORD");
    var v3 = Flyway.configure().dataSource(url, "postgres", password)
        .schemas("subscription").target("3").load();
    v3.migrate();
    properties.add("spring.datasource.url", () -> url);
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> password);
    properties.add("bookrush.security.issuer", () -> PRODUCT);
    jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] keys = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    jwks.createContext("/jwks", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, keys.length);
      try (var body = exchange.getResponseBody()) {
        body.write(keys);
      }
    });
    jwks.start();
    String jwksUrl = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks";
    properties.add("bookrush.security.jwk-set-uri", () -> jwksUrl);
    properties.add("subscription.workload-issuer", () -> PLATFORM);
    properties.add("subscription.cors-origins", () -> "https://client.test.invalid");
    properties.add("subscription.workload-jwks", () -> jwksUrl);
  }

  @Test
  void initializesV1AndUpgradesToSeedPlans() {
    assertEquals(2, db.queryForObject("SELECT count(*) FROM subscription.plan", Integer.class));
    assertEquals(4, db.queryForObject("SELECT count(*) FROM subscription.flyway_schema_history WHERE version IN ('1','2','3','4')", Integer.class));
  }

  @Test
  void migratesEmptyDatabaseInOnePass() throws Exception {
    String testUrl = System.getenv("SUBSCRIPTION_TEST_JDBC");
    String password = System.getenv("SUBSCRIPTION_TEST_PASSWORD");
    String name = "subscription_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = java.sql.DriverManager.getConnection(testUrl, "postgres", password);
         var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
      try {
        String url = testUrl.substring(0, testUrl.lastIndexOf('/') + 1) + name;
        var flyway = Flyway.configure().dataSource(url, "postgres", password).schemas("subscription").load();
        assertEquals(4, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
      } finally {
        statement.execute("DROP DATABASE " + name);
      }
    }
  }

  @Test
  void derivesRightsAndExpiresAtExclusiveEnd() {
    when(clock.instant()).thenReturn(NOW);
    TechnicalIdentity owner = owner();
    assertFalse(store.snapshot(owner).publishBooks());
    assertEquals(0, store.snapshot(owner).additionalSeats());
    activate(owner);
    assertTrue(store.snapshot(owner).publishBooks());
    assertEquals(5, store.snapshot(owner).additionalSeats());
    store.apply(owner, AGENT, event(2, CANCEL_AT_END));
    assertTrue(store.snapshot(owner).premiumFamily());
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    assertFalse(store.snapshot(owner).publishBooks());
    assertEquals(CommercialPolicy.State.EXPIRED, store.snapshot(owner).state());
    store.apply(owner, AGENT, event(3, EXPIRE));
    assertEquals(3, store.snapshot(owner).version());
  }

  @Test
  void suspendsReactivatesAndRevokesWithSameProductJwt() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    activate(owner);
    String reader = productToken(owner, "subscription.read");
    mvc.perform(get(ME).header("Authorization", "Bearer " + reader)).andExpect(jsonPath("$.publishBooks").value(true));
    store.apply(owner, AGENT, event(2, SUSPEND));
    mvc.perform(get(ME).header("Authorization", "Bearer " + reader)).andExpect(jsonPath("$.publishBooks").value(false));
    store.apply(owner, AGENT, event(3, REACTIVATE));
    assertTrue(store.snapshot(owner).publishBooks());
    store.apply(owner, AGENT, event(4, CANCEL_NOW));
    mvc.perform(get(ME).header("Authorization", "Bearer " + reader)).andExpect(jsonPath("$.additionalSeats").value(0));
  }

  @Test
  void replaysAndSerializesCompetingVersions() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    var activation = activate(owner);
    assertEquals(1, store.apply(owner, AGENT, activation));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM subscription.outbox WHERE event_id = ?", Integer.class, activation.eventId()));
    assertThrows(ResponseStatusException.class, () -> store.apply(owner, AGENT, event(3, CANCEL_NOW)));
    Callable<Integer> cancel = () -> {
      try {
        store.apply(owner, AGENT, event(2, CANCEL_NOW));
        return 200;
      } catch (ResponseStatusException error) {
        return error.getStatusCode().value();
      }
    };
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(java.util.List.of(cancel, cancel));
      assertEquals(java.util.Set.of(200, 409), java.util.Set.of(results.get(0).get(), results.get(1).get()));
    }
  }

  @Test
  void rejectsMissingInvalidAndUnderScopedCredentials() throws Exception {
    mvc.perform(get(ME)).andExpect(status().isUnauthorized());
    String wrongIssuer = bearer(PLATFORM, "reader", "bookrush-subscription", "subscription.read", "web");
    String wrongAudience = bearer(PRODUCT, "reader", "account", "subscription.read", "web");
    mvc.perform(get(ME).header("Authorization", "Bearer " + wrongIssuer)).andExpect(status().isUnauthorized());
    mvc.perform(get(ME).header("Authorization", "Bearer " + wrongAudience)).andExpect(status().isUnauthorized());
    String reader = productToken(owner(), "openid");
    mvc.perform(get(ME).header("Authorization", "Bearer " + reader)).andExpect(status().isForbidden());
    mvc.perform(post(SubscriptionSecurity.TRANSITION)).andExpect(status().isUnauthorized());
  }

  @Test
  void failsClosedWhenStorageCannotResolveRights() throws Exception {
    String reader = productToken(owner(), "subscription.read");
    db.execute("ALTER TABLE subscription.contract RENAME TO contract_unavailable_test");
    try {
      mvc.perform(get(ME).header("Authorization", "Bearer " + reader))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("DECISION_UNAVAILABLE"));
    } finally {
      db.execute("ALTER TABLE subscription.contract_unavailable_test RENAME TO contract");
    }
  }

  @Test
  void renewsOnlyByExtendingTheCurrentPeriod() {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    activate(owner);
    var renewedEnd = NOW.plusSeconds(7200);
    store.apply(owner, AGENT, new SubscriptionStore.BillingEvent(UUID.randomUUID(), 2, RENEW, NOW, renewedEnd));
    assertEquals(renewedEnd, store.snapshot(owner).effectiveUntil());
  }

  @Test
  void verifiesSeparateWorkloadClientsAndCurrentRights() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    activate(owner);
    String body = """
        {"owner":{"issuer":"%s","subject":"%s"},"entitlement":"PREMIUM_FAMILY"}
        """.formatted(owner.issuer(), owner.subject());
    String evaluator = bearer(PLATFORM, "family", "bookrush-subscription", "entitlements.evaluate", "bookrush-family-s2s");
    String spoofed = bearer(PLATFORM, "other", "bookrush-subscription", "entitlements.evaluate", "other-client");
    mvc.perform(post(SubscriptionSecurity.EVALUATE).contentType("application/json").content(body)
            .header("Authorization", "Bearer " + spoofed)).andExpect(status().isForbidden());
    mvc.perform(post(SubscriptionSecurity.EVALUATE).contentType("application/json").content(body)
            .header("Authorization", "Bearer " + evaluator))
        .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    mvc.perform(post(SubscriptionSecurity.TRANSITION).header("Authorization", "Bearer " + evaluator))
        .andExpect(status().isForbidden());
    String reader = productToken(owner, "subscription.transitions.write");
    mvc.perform(post(SubscriptionSecurity.TRANSITION).header("Authorization", "Bearer " + reader))
        .andExpect(status().isUnauthorized());
    String writer = bearer(PLATFORM, "billing", "bookrush-subscription",
        "subscription.transitions.write", "bookrush-billing-s2s");
    String transition = """
        {"owner":{"issuer":"%s","subject":"%s"},"eventId":"%s","sourceVersion":2,"operation":"CANCEL_NOW"}
        """.formatted(owner.issuer(), owner.subject(), UUID.randomUUID());
    mvc.perform(post(SubscriptionSecurity.TRANSITION).contentType("application/json").content(transition)
            .header("Authorization", "Bearer " + writer))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    mvc.perform(post(SubscriptionSecurity.EVALUATE).contentType("application/json").content(body)
            .header("Authorization", "Bearer " + evaluator))
        .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void permitsOnlyConfiguredBrowserOriginsOnSelfRoutes() throws Exception {
    mvc.perform(options(ME).header("Origin", "https://client.test.invalid")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://client.test.invalid"));
    mvc.perform(options(ME).header("Origin", "https://other.test.invalid")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void holdsGapThenReconcilesAndDeduplicatesRenewal() {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    var activation = new BillingInbox.Event("fixture", UUID.randomUUID().toString(), owner, 1, ACTIVATE,
        NOW, NOW.plusSeconds(3600));
    var renewal = new BillingInbox.Event("fixture", UUID.randomUUID().toString(), owner, 2, RENEW,
        NOW, NOW.plusSeconds(7200));
    var pending = billing.submit(renewal, AGENT);
    assertEquals(BillingInbox.Status.PENDING, pending.state());
    assertEquals(0L, pending.currentVersion());
    assertFalse(store.snapshot(owner).publishBooks());
    billing.submit(activation, AGENT);
    var applied = billing.submit(renewal, AGENT);
    assertEquals(BillingInbox.Status.APPLIED, applied.state());
    assertEquals(pending.eventId(), applied.eventId());
    assertEquals(applied, billing.submit(renewal, AGENT));
    assertEquals(NOW.plusSeconds(7200), store.snapshot(owner).effectiveUntil());
    assertEquals(1, db.queryForObject("SELECT count(*) FROM subscription.outbox WHERE event_id = ?",
        Integer.class, applied.eventId()));
    var changedPayload = new BillingInbox.Event("fixture", renewal.externalEventId(), owner, 2,
        CANCEL_NOW, null, null);
    assertEquals(409, assertThrows(ResponseStatusException.class,
        () -> billing.submit(changedPayload, AGENT)).getStatusCode().value());
    var stale = new BillingInbox.Event("fixture", UUID.randomUUID().toString(), owner, 1, CANCEL_NOW, null, null);
    assertEquals(BillingInbox.Status.STALE, billing.submit(stale, AGENT).state());
    assertTrue(store.snapshot(owner).publishBooks());
  }

  @Test
  void concurrentlyReplaysOneFinancialDeliveryOnlyOnce() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    var event = new BillingInbox.Event("fixture", UUID.randomUUID().toString(), owner, 1, ACTIVATE,
        NOW, NOW.plusSeconds(3600));
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    Callable<BillingInbox.Receipt> deliver = () -> {
      barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
      return billing.submit(event, AGENT);
    };
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executor.invokeAll(java.util.List.of(deliver, deliver));
      var receipt = results.getFirst().get();
      assertEquals(receipt, results.getLast().get());
      assertEquals(BillingInbox.Status.APPLIED, receipt.state());
      assertEquals(1, db.queryForObject("SELECT count(*) FROM subscription.outbox WHERE event_id = ?",
          Integer.class, receipt.eventId()));
    }
  }

  @Test
  void durableReceiptSurvivesApplicationFailureAndRetry() {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    var event = new BillingInbox.Event("fixture", UUID.randomUUID().toString(), owner, 1, ACTIVATE,
        NOW, NOW.plusSeconds(3600));
    db.execute("ALTER TABLE subscription.outbox RENAME TO outbox_unavailable_test");
    try {
      assertThrows(org.springframework.dao.DataAccessException.class, () -> billing.submit(event, AGENT));
      assertFalse(store.snapshot(owner).premiumFamily());
      assertEquals("PENDING", db.queryForObject(
          "SELECT state FROM subscription.billing_inbox WHERE external_event_id = ?", String.class, event.externalEventId()));
    } finally {
      db.execute("ALTER TABLE subscription.outbox_unavailable_test RENAME TO outbox");
    }
    assertEquals(BillingInbox.Status.APPLIED, billing.submit(event, AGENT).state());
    assertTrue(store.snapshot(owner).premiumFamily());
  }

  @Test
  void acceptsBillingOnlyFromAuthorizedPlatformWriter() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    String path = SubscriptionSecurity.BILLING_EVENT;
    String body = """
        {"billingSource":"fixture","externalEventId":"%s","owner":{"issuer":"%s","subject":"%s"},
        "sourceVersion":2,"operation":"CANCEL_NOW"}
        """.formatted(UUID.randomUUID(), owner.issuer(), owner.subject());
    mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    String product = productToken(owner, "subscription.transitions.write");
    mvc.perform(post(path).contentType("application/json").content(body)
        .header("Authorization", "Bearer " + product)).andExpect(status().isUnauthorized());
    String wrongClient = bearer(PLATFORM, "other", "bookrush-subscription",
        "subscription.transitions.write", "other-client");
    mvc.perform(post(path).contentType("application/json").content(body)
        .header("Authorization", "Bearer " + wrongClient)).andExpect(status().isForbidden());
    String writer = bearer(PLATFORM, "billing", "bookrush-subscription",
        "subscription.transitions.write", "bookrush-billing-s2s");
    mvc.perform(post(path).contentType("application/json").content(body)
        .header("Authorization", "Bearer " + writer)).andExpect(status().isAccepted())
        .andExpect(jsonPath("$.currentVersion").value(0));
    assertFalse(store.snapshot(owner).publishBooks());
  }

  @Test
  void mockCheckoutUsesInboxAndReplaysApprovalForTheAuthenticatedAccount() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    var reader = productToken(owner, "subscription.read");
    var key = UUID.randomUUID();
    assertFalse(store.snapshot(owner).publishBooks());
    var request = post(SubscriptionSecurity.CHECKOUT).header("Authorization", "Bearer " + reader)
        .header("Idempotency-Key", key);
    var receipt = mvc.perform(request).andExpect(status().isOk())
        .andExpect(jsonPath("$.simulation").value(true)).andExpect(jsonPath("$.realPayment").value(false))
        .andExpect(jsonPath("$.state").value("APPLIED")).andReturn().getResponse().getContentAsString();
    mvc.perform(request).andExpect(status().isOk()).andExpect(content().json(receipt));
    assertEquals(1, store.snapshot(owner).version());
    mvc.perform(get(ME).header("Authorization", "Bearer " + reader))
        .andExpect(jsonPath("$.publishBooks").value(true)).andExpect(jsonPath("$.additionalSeats").value(5));
    assertEquals(NOW.plusSeconds(30 * 86400L), store.snapshot(owner).effectiveUntil());
    assertEquals(1, db.queryForObject("""
        SELECT count(*) FROM subscription.billing_inbox i JOIN subscription.outbox o ON i.event_id = o.event_id
        WHERE i.identity_subject = ? AND i.billing_source = 'mock'
        """, Integer.class, owner.subject()));
  }

  @Test
  void mockRetainsAccessUntilCancellationDeadlineAndRenewsExplicitly() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    mockCheckout(owner, UUID.randomUUID());
    String writer = billingWriter();
    mvc.perform(simulation(owner, writer, "RENEW", UUID.randomUUID())).andExpect(status().isOk());
    var renewedEnd = NOW.plusSeconds(60 * 86400L);
    assertEquals(renewedEnd, store.snapshot(owner).effectiveUntil());
    mvc.perform(simulation(owner, writer, "CANCEL", UUID.randomUUID())).andExpect(status().isOk());
    when(clock.instant()).thenReturn(renewedEnd.minusSeconds(1));
    assertTrue(store.snapshot(owner).premiumFamily());
    when(clock.instant()).thenReturn(renewedEnd);
    assertFalse(store.snapshot(owner).premiumFamily());
    assertEquals(CommercialPolicy.State.EXPIRED, store.snapshot(owner).state());
  }

  @Test
  void mockDeclineLeavesFreeAndRefundAndChargebackRevokeWithTheSameJwt() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    String writer = billingWriter();
    var declined = owner();
    var attempt = simulation(declined, writer, "DECLINE", UUID.randomUUID());
    String receipt = mvc.perform(attempt).andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DECLINED")).andReturn().getResponse().getContentAsString();
    mvc.perform(attempt).andExpect(content().json(receipt));
    assertEquals(0L, store.snapshot(declined).version());
    assertFalse(store.snapshot(declined).publishBooks());
    for (String scenario : java.util.List.of("REFUND", "CHARGEBACK")) {
      var owner = owner();
      String reader = mockCheckout(owner, UUID.randomUUID());
      if (scenario.equals("CHARGEBACK")) {
        mvc.perform(simulation(owner, writer, "CANCEL", UUID.randomUUID())).andExpect(status().isOk());
      }
      mvc.perform(simulation(owner, writer, scenario, UUID.randomUUID())).andExpect(status().isOk());
      mvc.perform(get(ME).header("Authorization", "Bearer " + reader))
          .andExpect(jsonPath("$.publishBooks").value(false))
          .andExpect(jsonPath("$.state").value(scenario.equals("REFUND") ? "CANCELED" : "SUSPENDED"));
    }
  }

  @Test
  void mockCheckoutRecoversStableRequestAfterFailedOutboxWrite() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    var owner = owner();
    UUID key = UUID.randomUUID();
    db.execute("ALTER TABLE subscription.outbox RENAME TO outbox_unavailable_test");
    UUID externalId;
    try {
      mvc.perform(post(SubscriptionSecurity.CHECKOUT).header("Authorization", "Bearer " +
          productToken(owner, "subscription.read")).header("Idempotency-Key", key))
          .andExpect(status().isServiceUnavailable());
      assertFalse(store.snapshot(owner).publishBooks());
      externalId = db.queryForObject("SELECT external_event_id FROM subscription.mock_billing_request WHERE request_id = ?",
          UUID.class, key);
      assertEquals("PENDING", db.queryForObject(
          "SELECT state FROM subscription.billing_inbox WHERE external_event_id = ?", String.class, externalId.toString()));
    } finally {
      db.execute("ALTER TABLE subscription.outbox_unavailable_test RENAME TO outbox");
    }
    mvc.perform(post(SubscriptionSecurity.CHECKOUT).header("Authorization", "Bearer " +
        productToken(owner, "subscription.read")).header("Idempotency-Key", key)).andExpect(status().isOk())
        .andExpect(jsonPath("$.externalEventId").value(externalId.toString()));
    assertTrue(store.snapshot(owner).publishBooks());
  }

  @Test
  void mockRejectsMissingCredentialsAndUserSimulations() throws Exception {
    var owner = owner();
    mvc.perform(post(SubscriptionSecurity.CHECKOUT).header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(SubscriptionSecurity.CHECKOUT).header("Authorization", "Bearer " + productToken(owner, "openid"))
        .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isForbidden());
    mvc.perform(simulation(owner, productToken(owner, "subscription.transitions.write"), "REFUND", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
    mvc.perform(simulation(owner, billingWriter(), "APPROVE", UUID.randomUUID())).andExpect(status().isBadRequest());
  }

  private String mockCheckout(TechnicalIdentity owner, UUID requestId) throws Exception {
    String reader = productToken(owner, "subscription.read");
    mvc.perform(post(SubscriptionSecurity.CHECKOUT).header("Authorization", "Bearer " + reader)
        .header("Idempotency-Key", requestId)).andExpect(status().isOk());
    return reader;
  }

  private static String billingWriter() throws Exception {
    return bearer(PLATFORM, "billing", "bookrush-subscription", "subscription.transitions.write", "bookrush-billing-s2s");
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder simulation(
      TechnicalIdentity owner, String writer, String scenario, UUID key) {
    return post(SubscriptionSecurity.MOCK_SIMULATE).contentType("application/json")
        .header("Authorization", "Bearer " + writer).header("Idempotency-Key", key)
        .content("""
            {"owner":{"issuer":"%s","subject":"%s"},"scenario":"%s"}
            """.formatted(owner.issuer(), owner.subject(), scenario));
  }

  private static String productToken(TechnicalIdentity owner, String scope) throws Exception {
    return bearer(PRODUCT, owner.subject(), "bookrush-subscription", scope, "test-web");
  }

  private static String bearer(String issuer, String subject, String audience, String scope, String client)
      throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience)
        .expirationTime(Date.from(Instant.now().plusSeconds(120)))
        .claim("scope", scope).claim("azp", client).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
        .keyID(KEY.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(KEY));
    return jwt.serialize();
  }

  private static TechnicalIdentity owner() {
    return new TechnicalIdentity(PRODUCT, UUID.randomUUID().toString());
  }

  private SubscriptionStore.BillingEvent activate(TechnicalIdentity owner) {
    var event = new SubscriptionStore.BillingEvent(UUID.randomUUID(), 1, ACTIVATE, NOW, NOW.plusSeconds(3600));
    store.apply(owner, AGENT, event);
    return event;
  }

  private static SubscriptionStore.BillingEvent event(long version, CommercialPolicy.Operation operation) {
    return new SubscriptionStore.BillingEvent(UUID.randomUUID(), version, operation, null, null);
  }
}
