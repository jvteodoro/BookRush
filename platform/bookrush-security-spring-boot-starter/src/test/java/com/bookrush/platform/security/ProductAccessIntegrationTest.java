package com.bookrush.platform.security;

import static com.bookrush.platform.security.ProductAccessRoutes.Action.*;
import static com.bookrush.platform.security.ProductAccessRoutes.Type.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

class ProductAccessIntegrationTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private static final String CORRELATION = "request-123";
  private static final String PREFIX = "bookrush.security.product-access.";
  private static final AtomicInteger DECISIONS = new AtomicInteger();
  private static final AtomicInteger TOKENS = new AtomicInteger();
  private static final AtomicInteger CHECKS = new AtomicInteger();
  private static volatile String decisionMode;
  private static volatile String tokenMode;
  private static volatile JsonNode lastRequest;
  private static RSAKey key;
  private static HttpServer upstream;
  private static ExecutorService executor;
  private static ServletWebServerApplicationContext normal;
  private static ServletWebServerApplicationContext custom;
  private static String origin;

  @BeforeAll
  static void start() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    origin = "http://127.0.0.1:" + upstream.getAddress().getPort();
    executor = Executors.newVirtualThreadPerTaskExecutor();
    upstream.setExecutor(executor);
    upstream.createContext("/jwks", exchange -> send(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
    upstream.createContext("/token", ProductAccessIntegrationTest::token);
    upstream.createContext("/api/internal/v1/product-access/decisions", ProductAccessIntegrationTest::decision);
    upstream.start();
    normal = application(Consumer.class);
    custom = application(Consumer.class, CustomChain.class);
  }

  @AfterAll
  static void stop() {
    if (custom != null) custom.close();
    if (normal != null) normal.close();
    if (upstream != null) upstream.stop(0);
    if (executor != null) executor.close();
  }

  @BeforeEach
  void reset() {
    decisionMode = "allow";
    tokenMode = "valid";
    CHECKS.set(0);
  }

  @Test
  void appliesDecisionWithBackendOwnershipAndCorrelation() throws Exception {
    var response = call(normal, "GET", "/book/one", human("reader"), Map.of(
        "X-Subject", "forged", "X-Issuer", "forged", "X-Owner", "forged"));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Correlation-ID")).contains(CORRELATION);
    assertThat(lastRequest.path("actor").path("issuer").asText()).isEqualTo(issuer());
    assertThat(lastRequest.path("actor").path("subject").asText()).isEqualTo("reader");
    assertThat(lastRequest.path("resource").path("owner").path("subject").asText()).isEqualTo("stored-owner");
    assertThat(lastRequest.path("resource").path("id").asText()).isEqualTo("one");
    assertThat(lastRequest.path("authentication").path("clientId").asText()).isEqualTo("web");
    assertThat(lastRequest.path("authentication").path("audience").asText()).isEqualTo("consumer");
    assertThat(lastRequest.path("authentication").path("scopes").get(0).asText()).isEqualTo("read");
    assertThat(lastRequest.path("correlationId").asText()).isEqualTo(CORRELATION);
  }

  @Test
  void authenticationAndScopeBeforeCallingEvaluator() throws Exception {
    int before = DECISIONS.get();
    for (String token : List.of("", "not-a-token", signed(origin + "/other", "consumer", "web", "read", "reader", 120),
        signed(issuer(), "other", "web", "read", "reader", 120),
        signed(issuer(), "consumer", "web", "read", "reader", -1))) {
      assertThat(get("/book/one", token).statusCode()).isEqualTo(401);
    }
    assertThat(get("/book/one", signed(issuer(), "consumer", "web", "unknown", "reader", 120)).statusCode()).isEqualTo(403);
    assertThat(get("/book/one", signed(issuer(), "consumer", "service", "read", "robot", 120)).statusCode()).isEqualTo(403);
    assertThat(DECISIONS.get()).isEqualTo(before);
  }

  @Test
  void customChainCanKeepAdminPolicyAndCannotBypassOptIn() throws Exception {
    int before = DECISIONS.get();
    assertThat(call(custom, "GET", "/admin", human("operator"), Map.of()).statusCode()).isEqualTo(403);
    var operator = signed(issuer(), "consumer", "ops", "admin", "operator", 120);
    assertThat(call(custom, "GET", "/admin", operator, Map.of()).statusCode()).isEqualTo(200);
    assertThat(DECISIONS.get()).isEqualTo(before);
    assertThat(call(custom, "GET", "/book/one", "", Map.of()).statusCode()).isEqualTo(401);
    decisionMode = "deny";
    assertThat(call(custom, "GET", "/book/one", human("reader"), Map.of()).statusCode()).isEqualTo(403);
    assertThat(call(custom, "GET", "/unmapped", human("reader"), Map.of()).statusCode()).isEqualTo(503);
  }

  @Test
  void restrictedActorCanUseOwnSessionAndInvites() throws Exception {
    decisionMode = "restricted";
    String minor = human("minor");
    assertThat(get("/book/one", minor).statusCode()).isEqualTo(403);
    assertThat(get("/session", minor).statusCode()).isEqualTo(200);
    assertThat(call(normal, "POST", "/invites/own", minor, Map.of()).statusCode()).isEqualTo(200);
    int before = DECISIONS.get();
    assertThat(call(normal, "POST", "/invites/other", minor, Map.of()).statusCode()).isEqualTo(404);
    assertThat(call(normal, "POST", "/invites/hidden", minor, Map.of()).statusCode()).isEqualTo(404);
    assertThat(DECISIONS.get()).isEqualTo(before);
    assertThat(call(custom, "GET", "/session", "", Map.of()).statusCode()).isEqualTo(401);
  }

  @ParameterizedTest
  @ValueSource(strings = {"offline", "timeout", "slow-body", "invalid", "duplicate", "trailing", "version", "expired",
      "missing-facts", "invalid-facts", "wrong-correlation", "wrong-reason", "unexpected", "redirect", "oversized"})
  void invalidOrUnavailableDecisionFailsClosed(String mode) throws Exception {
    decisionMode = mode;
    var response = get("/book/one", human("reader"));
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(JSON.readTree(response.body()).path("correlationId").asText()).isEqualTo(CORRELATION);
    assertThat(JSON.readTree(response.body()).path("code").asText()).isEqualTo("DECISION_UNAVAILABLE");
  }

  @Test
  void checksWorkloadCredentialsAndRenewsAfterServiceRejectsToken() throws Exception {
    String human = human("reader");
    for (String badToken : List.of("offline", "issuer", "audience", "scope", "client", "expired")) {
      decisionMode = "workload-rejected";
      assertThat(get("/book/one", human).statusCode()).isEqualTo(503);
      int before = DECISIONS.get();
      tokenMode = badToken;
      decisionMode = "allow";
      assertThat(get("/book/one", human).statusCode()).isEqualTo(503);
      assertThat(DECISIONS.get()).isEqualTo(before);
      tokenMode = "valid";
      assertThat(get("/book/one", human).statusCode()).isEqualTo(200);
    }
    assertThat(normal.getBean(SimpleMeterRegistry.class).counter("bookrush.product.access.s2s.errors").count()).isPositive();
  }

  @Test
  void renewsCredentialWithinItsExpiryMargin() throws Exception {
    String human = human("reader");
    decisionMode = "workload-rejected";
    assertThat(get("/book/one", human).statusCode()).isEqualTo(503);
    int before = TOKENS.get();
    decisionMode = "allow";
    tokenMode = "short-lived";
    assertThat(get("/book/one", human).statusCode()).isEqualTo(200);
    assertThat(get("/book/one", human).statusCode()).isEqualTo(200);
    assertThat(TOKENS.get() - before).isEqualTo(2);
  }

  @Test
  void sameJwtUsedAfterRevocationAndMajorityCannotKeepAccess() throws Exception {
    String jwt = human("reader");
    assertThat(get("/book/one", jwt).statusCode()).isEqualTo(200);
    decisionMode = "revoked";
    assertThat(get("/book/one", jwt).statusCode()).isEqualTo(403);
    decisionMode = "allow";
    assertThat(get("/history", jwt).statusCode()).isEqualTo(200);
    decisionMode = "adult";
    assertThat(get("/history", jwt).statusCode()).isEqualTo(403);
  }

  @Test
  void concurrencyOnlyReusesWorkloadTokenAndRechecksFinalOperation() throws Exception {
    String jwt = human("reader");
    assertThat(get("/book/one", jwt).statusCode()).isEqualTo(200);
    int before = DECISIONS.get();
    int tokenCalls = TOKENS.get();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 12; i++) calls.add(pool.submit(() -> get("/book/one", jwt).statusCode()));
      for (var call : calls) assertThat(call.get(5, TimeUnit.SECONDS)).isEqualTo(200);
    }
    assertThat(DECISIONS.get() - before).isEqualTo(12);
    assertThat(TOKENS.get()).isEqualTo(tokenCalls);
    decisionMode = "recheck";
    var rejected = call(normal, "POST", "/sensitive", jwt, Map.of());
    assertThat(rejected.statusCode()).isEqualTo(403);
    assertThat(JSON.readTree(rejected.body()).path("correlationId").asText()).isEqualTo(CORRELATION);
    assertThat(CHECKS.get()).isEqualTo(2);
  }

  @Test
  void headInheritsGetAndCorsPreflightUsesConsumerPolicy() throws Exception {
    decisionMode = "deny";
    var head = call(custom, "HEAD", "/book/one", human("reader"), Map.of());
    assertThat(head.statusCode()).isEqualTo(403);
    assertThat(head.body()).isEmpty();
    int before = DECISIONS.get();
    var headers = Map.of("Origin", "https://test.example", "Access-Control-Request-Method", "GET",
        "Access-Control-Request-Headers", "Authorization");
    var preflight = call(custom, "OPTIONS", "/book/one", "", headers);
    assertThat(preflight.statusCode()).isBetween(200, 299);
    assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).contains("https://test.example");
    assertThat(call(custom, "OPTIONS", "/book/one", "", Map.of("Origin", "https://other.example",
        "Access-Control-Request-Method", "GET")).statusCode()).isEqualTo(403);
    var defaultPreflight = call(normal, "OPTIONS", "/book/one", "", headers);
    assertThat(defaultPreflight.statusCode()).isBetween(200, 299);
    assertThat(defaultPreflight.headers().firstValue("Access-Control-Allow-Origin")).contains("https://test.example");
    assertThat(DECISIONS.get()).isEqualTo(before);
  }

  @Test
  void enabledWithoutConfigFailsStartup() {
    new WebApplicationContextRunner().withUserConfiguration(Consumer.class)
        .withPropertyValues(PREFIX + "enabled=true")
        .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
  }

  @Test
  void optedOutKeepsDefaultBehaviorWithoutEvaluator() {
    new WebApplicationContextRunner().withUserConfiguration(Consumer.class)
        .run(context -> assertThat(context).doesNotHaveBean(ProductAccessAuthorization.class));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "BOOKRUSH_TEST_PRODUCT_ACCESS_ISSUER", matches = "https?://.+")
  void usesRealKeycloakServiceAccountWithContractEvaluator() throws Exception {
    String oidc = System.getenv("BOOKRUSH_TEST_PRODUCT_ACCESS_ISSUER");
    decisionMode = "real-keycloak";
    try (var real = application(oidc, oidc + "/protocol/openid-connect/token",
        oidc + "/protocol/openid-connect/certs", Consumer.class)) {
      assertThat(call(real, "GET", "/book/one", human("reader"), Map.of()).statusCode()).isEqualTo(200);
    }
  }

  private static HttpResponse<String> get(String path, String jwt) throws Exception {
    return call(normal, "GET", path, jwt, Map.of());
  }

  private static HttpResponse<String> call(ServletWebServerApplicationContext app, String method, String path,
      String jwt, Map<String, String> headers) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path))
        .timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody()).header("X-Correlation-ID", CORRELATION);
    if (!jwt.isEmpty()) builder.header("Authorization", "Bearer " + jwt);
    headers.forEach(builder::header);
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static ServletWebServerApplicationContext application(Class<?>... sources) {
    return application(workloadIssuer(), origin + "/token", origin + "/jwks", sources);
  }

  private static ServletWebServerApplicationContext application(String s2sIssuer, String tokenUri, String jwksUri,
      Class<?>... sources) {
    return (ServletWebServerApplicationContext) new SpringApplicationBuilder(sources).properties(
        "server.port=0", "spring.main.banner-mode=off", "bookrush.security.enabled=true",
        "bookrush.security.issuer=" + issuer(), "bookrush.security.audience=consumer",
        "bookrush.security.jwk-set-uri=" + origin + "/jwks",
        PREFIX + "enabled=true", PREFIX + "allow-insecure-http=true",
        PREFIX + "decision-base-uri=" + origin, PREFIX + "token-uri=" + tokenUri,
        PREFIX + "jwk-set-uri=" + jwksUri, PREFIX + "workload-issuer=" + s2sIssuer,
        PREFIX + "client-id=workload", PREFIX + "client-secret=synthetic-secret", PREFIX + "request-timeout=750ms").run();
  }

  private static String issuer() { return origin + "/realms/product"; }
  private static String workloadIssuer() { return origin + "/realms/platform"; }
  private static String human(String subject) throws Exception {
    return signed(issuer(), "consumer", "web", "read", subject, 120);
  }

  private static String signed(String issuer, String audience, String client, String scope, String subject, long seconds)
      throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience)
        .claim("azp", client).claim("scope", scope).expirationTime(Date.from(Instant.now().plusSeconds(seconds))).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static void token(HttpExchange exchange) throws java.io.IOException {
    TOKENS.incrementAndGet();
    var form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (!form.equals("grant_type=client_credentials&client_id=workload&client_secret=synthetic-secret&scope=product-access.evaluate")
        || tokenMode.equals("offline")) {
      send(exchange, 401, "{}");
      return;
    }
    try {
      var jwt = signed(tokenMode.equals("issuer") ? issuer() : workloadIssuer(),
          tokenMode.equals("audience") ? "consumer" : "bookrush-family", tokenMode.equals("client") ? "another" : "workload",
          tokenMode.equals("scope") ? "unrelated" : "product-access.evaluate", "technical",
          switch (tokenMode) { case "expired" -> -1; case "short-lived" -> 8; default -> 120; });
      send(exchange, 200, JSON.writeValueAsString(Map.of("access_token", jwt, "token_type", "Bearer", "expires_in", 120)));
    } catch (Exception unexpected) {
      send(exchange, 500, "{}");
    }
  }

  private static void decision(HttpExchange exchange) throws java.io.IOException {
    DECISIONS.incrementAndGet();
    String mode = decisionMode;
    try {
      var wire = exchange.getRequestHeaders().getFirst("Authorization");
      var credential = SignedJWT.parse(wire.substring("Bearer ".length())).getJWTClaimsSet();
      String expectedIssuer = mode.equals("real-keycloak")
          ? System.getenv("BOOKRUSH_TEST_PRODUCT_ACCESS_ISSUER") : workloadIssuer();
      if (!expectedIssuer.equals(credential.getIssuer()) || !"workload".equals(credential.getStringClaim("azp"))
          || !credential.getAudience().contains("bookrush-family")
          || !ProductAccessAuthorization.scopes(credential.getStringClaim("scope")).contains("product-access.evaluate")
          || mode.equals("workload-rejected")) {
        send(exchange, 401, "{}");
        return;
      }
      var request = JSON.readTree(exchange.getRequestBody().readAllBytes());
      lastRequest = request;
      var body = JSON.createObjectNode();
      var action = request.path("action").asText();
      boolean allowed = switch (mode) {
        case "deny", "revoked" -> false;
        case "adult" -> !action.equals("SUPERVISE_HISTORY");
        case "restricted" -> action.equals("SESSION_VIEW") || action.equals("INVITE_SELF");
        case "recheck" -> CHECKS.incrementAndGet() == 1;
        default -> true;
      };
      body.put("allowed", allowed);
      if (!allowed) body.put("reason", mode.equals("adult") ? "TARGET_NOT_ELIGIBLE" : "ACCOUNT_RESTRICTED");
      body.put("validUntil", Instant.now().plusSeconds(mode.equals("expired") ? -1 : 30).toString());
      body.put("policyVersion", mode.equals("version") ? "2" : "1");
      String correlation = request.path("correlationId").asText();
      if (!correlation.equals(exchange.getRequestHeaders().getFirst("X-Correlation-ID"))) {
        send(exchange, 400, "{}");
        return;
      }
      body.put("correlationId", mode.equals("wrong-correlation") ? "another" : correlation);
      if (!mode.equals("missing-facts")) body.putObject("factVersions").put("eligibility", "v1");
      switch (mode) {
        case "invalid-facts" -> body.putObject("factVersions").put("entitlement", 1);
        case "unexpected" -> body.put("privateData", true);
        case "wrong-reason" -> body.put("reason", "ACCOUNT_RESTRICTED");
        case "timeout" -> Thread.sleep(1500);
        case "slow-body" -> {
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, 0);
          exchange.getResponseBody().flush();
          Thread.sleep(1500);
          exchange.getResponseBody().write(body.toString().getBytes(StandardCharsets.UTF_8));
          exchange.close();
          return;
        }
        case "redirect" -> exchange.getResponseHeaders().set("Location", origin + "/token");
        default -> { }
      }
      switch (mode) {
        case "offline" -> send(exchange, 503, "{}");
        case "redirect" -> send(exchange, 307, "{}");
        case "invalid" -> send(exchange, 200, "invalid");
        case "oversized" -> send(exchange, 200, " ".repeat(65_537));
        case "trailing" -> send(exchange, 200, body + "{}");
        case "duplicate" -> send(exchange, 200, "{\"allowed\":false," + body.toString().substring(1));
        default -> send(exchange, 200, body.toString());
      }
    } catch (Exception unexpected) {
      exchange.close();
    }
  }

  private static void send(HttpExchange exchange, int status, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class Consumer {
    @Bean SimpleMeterRegistry testMetrics() { return new SimpleMeterRegistry(); }
    @Bean Endpoints endpoints(org.springframework.beans.factory.ObjectProvider<ProductAccessAuthorization> authorization) {
      return new Endpoints(authorization);
    }
    @Bean ProductAccessRoutes productRoutes() {
      return ProductAccessRoutes.builder()
          .protect("GET", "/book/{id}", new ProductAccessRoutes.Policy(issuer(), "consumer", Set.of("web"), Set.of("read"),
              PRODUCT_USE, BOOK, true, (request, actor) -> new ProductAccessRoutes.Resource(BOOK, pathId(request),
                  new TechnicalIdentity(issuer(), "stored-owner"))))
          .protect("GET", "/history", new ProductAccessRoutes.Policy(issuer(), "consumer", Set.of("web"), Set.of("read"),
              SUPERVISE_HISTORY, READING_HISTORY, true, (request, actor) -> new ProductAccessRoutes.Resource(
                  READING_HISTORY, null, new TechnicalIdentity(issuer(), "minor"))))
          .protect("GET", "/session", new ProductAccessRoutes.Policy(issuer(), "consumer", Set.of("web"), Set.of("read"),
              SESSION_VIEW, SESSION, true, (request, actor) -> new ProductAccessRoutes.Resource(SESSION, null, actor)))
          .protect("POST", "/invites/{id}", new ProductAccessRoutes.Policy(issuer(), "consumer", Set.of("web"), Set.of("read"),
              INVITE_SELF, INVITATION, true, (request, actor) -> {
                String id = pathId(request);
                if ("hidden".equals(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                return new ProductAccessRoutes.Resource(INVITATION, id,
                    new TechnicalIdentity(issuer(), "own".equals(id) ? "minor" : "other"));
              }))
          .protect("POST", "/sensitive", new ProductAccessRoutes.Policy(issuer(), "consumer", Set.of("web"), Set.of("read"),
              PUBLISH_WRITE, SUBMISSION, true, (request, actor) -> new ProductAccessRoutes.Resource(SUBMISSION, null, actor)))
          .existingSecurity("GET", "/admin")
          .existingSecurity("POST", "/error")
          .build();
    }
  }

  private static String pathId(HttpServletRequest request) {
    return (String) ((Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).get("id");
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomChain {
    @Bean SecurityFilterChain customSecurity(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable()).cors(org.springframework.security.config.Customizer.withDefaults())
          .authorizeHttpRequests(auth -> auth.requestMatchers("/admin").hasAuthority("SCOPE_admin").anyRequest().permitAll())
          .oauth2ResourceServer(oauth -> oauth.jwt(org.springframework.security.config.Customizer.withDefaults()))
          .build();
    }
  }

  @RestController
  record Endpoints(org.springframework.beans.factory.ObjectProvider<ProductAccessAuthorization> authorization) {
    @CrossOrigin(origins = "https://test.example")
    @GetMapping("/book/{id}") String book(@PathVariable("id") String id) { return id; }
    @GetMapping("/history") String history() { return "history"; }
    @GetMapping("/admin") String admin() { return "admin"; }
    @GetMapping("/session") String session() { return "session"; }
    @GetMapping("/unmapped") String unmapped() { return "unmapped"; }
    @PostMapping("/invites/{id}") String invite() { return "invite"; }
    @PostMapping("/sensitive") String sensitive(HttpServletRequest request) {
      authorization.getObject().recheck(request);
      return "written";
    }
  }
}
