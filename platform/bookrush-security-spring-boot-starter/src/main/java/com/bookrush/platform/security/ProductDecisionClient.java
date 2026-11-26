package com.bookrush.platform.security;

import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

final class ProductDecisionClient {
  private static final String PATH = "/api/internal/v1/product-access/decisions";
  private static final Pattern DATE_TIME = Pattern.compile(
      "[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?(?:[Zz]|[+-][0-9]{2}:[0-9]{2})");
  private static final Set<String> FIELDS = Set.of("allowed", "reason", "correlationId", "policyVersion",
      "validUntil", "factVersions");
  private static final Set<String> REASONS = Set.of("ACCOUNT_RESTRICTED", "ACCOUNT_SUSPENDED",
      "AGE_INELIGIBLE", "ENTITLEMENT_REQUIRED", "DELEGATION_REQUIRED", "OWNERSHIP_REQUIRED",
      "SUPERVISION_REQUIRED", "TARGET_NOT_ELIGIBLE", "SCOPE_REQUIRED");

  record Authentication(String clientId, String audience, Set<String> scopes) {}
  record Request(TechnicalIdentity actor, ProductAccessRoutes.Action action, Authentication authentication,
                 ProductAccessRoutes.Resource resource, String correlationId) {}
  private record WorkloadToken(String value, Instant renewAt) {}

  static final class Unavailable extends RuntimeException {
    Unavailable() {
      super("DECISION_UNAVAILABLE");
    }
  }

  private final ProductAccessProperties config;
  private final Clock clock;
  private final MeterRegistry metrics;
  private final HttpClient http;
  private final JwtDecoder decoder;
  private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
  private final ReentrantLock tokenLock = new ReentrantLock();
  private final AtomicReference<WorkloadToken> token = new AtomicReference<>();

  ProductDecisionClient(ProductAccessProperties config, Clock clock, MeterRegistry metrics) throws java.net.MalformedURLException {
    this.config = config;
    this.clock = clock;
    this.metrics = metrics;
    http = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
    var processor = new DefaultJWTProcessor<SecurityContext>();
    var source = JWKSourceBuilder.<SecurityContext>create(config.jwkSetUri().toURL(), url -> {
      try {
        var response = send(HttpRequest.newBuilder(config.jwkSetUri()).timeout(config.requestTimeout()).GET().build());
        if (response.statusCode() != 200) throw new Unavailable();
        return new Resource(new String(response.body(), StandardCharsets.UTF_8), "application/json");
      } catch (Exception invalid) {
        if (invalid instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new IOException("Workload JWKS unavailable");
      }
    }).refreshAheadCache(false).build();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    var jwt = new NimbusJwtDecoder(processor);
    jwt.setJwtValidator(JwtValidators.createDefaultWithIssuer(config.workloadIssuer()));
    decoder = jwt;
  }

  boolean allowed(Request request) {
    long start = System.nanoTime();
    String outcome = "unavailable";
    try {
      var response = post(PATH, request, request.correlationId());
      boolean allowed = validate(response, request.correlationId());
      outcome = allowed ? "allowed" : "denied";
      return allowed;
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new Unavailable();
    } finally {
      if (metrics != null) {
        metrics.counter("bookrush.product.access.decisions", "outcome", outcome).increment();
        metrics.timer("bookrush.product.access.latency").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      }
    }
  }

  ProductAccessAuthorization.HistoryWindow historyWindow(TechnicalIdentity actor, java.util.UUID memberId,
      String scope, String correlation) {
    try {
      var response = post("/api/internal/v1/family/supervision/history-window",
          java.util.Map.of("guardian", actor, "memberId", memberId, "scope", scope), correlation, true);
      if (response.statusCode() == 404) return null;
      if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip()
          .equalsIgnoreCase("application/json")) throw new Unavailable();
      JsonNode body = json.readTree(response.body());
      JsonNode facts = body.path("facts");
      var target = body.path("minor");
      var minor = new TechnicalIdentity(target.path("issuer").textValue(), target.path("subject").textValue());
      if (!actor.issuer().equals(minor.issuer()) || !facts.path("allowed").isBoolean()) throw new Unavailable();
      if (!facts.path("allowed").booleanValue()) {
        if (!Set.of("SUPERVISION_REQUIRED", "TARGET_NOT_ELIGIBLE").contains(facts.path("reason").asText())) {
          throw new Unavailable();
        }
        return new ProductAccessAuthorization.HistoryWindow(minor, null, null, null, null);
      }
      Instant from = Instant.parse(facts.path("windowFrom").textValue());
      Instant until = Instant.parse(facts.path("windowUntil").textValue());
      Instant expires = Instant.parse(facts.path("validUntil").textValue());
      var version = facts.path("grantVersion");
      if (!memberId.equals(java.util.UUID.fromString(facts.path("grantId").textValue()))
          || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0
          || !from.isBefore(until) || until.isAfter(expires) || !clock.instant().isBefore(expires)
          || until.isAfter(clock.instant()) || from.isBefore(until.minus(java.time.Duration.ofDays(90)))) {
        throw new Unavailable();
      }
      return new ProductAccessAuthorization.HistoryWindow(minor, version.longValue(), from, until, expires);
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new Unavailable();
    }
  }

  Set<String> discoverableProfiles(Set<String> candidates, String correlation) {
    try {
      var response = post("/api/internal/v1/product-access/profile-discovery", java.util.Map.of("subjects", candidates),
          correlation);
      if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip()
          .equalsIgnoreCase("application/json")) throw new Unavailable();
      JsonNode body = json.readTree(response.body());
      if (body == null || !body.isObject() || body.size() != 3 || !correlation.equals(body.path("correlationId").textValue())
          || !clock.instant().isBefore(Instant.parse(body.path("validUntil").textValue()))
          || !body.path("subjects").isArray()) throw new Unavailable();
      var returned = new java.util.HashSet<String>();
      for (JsonNode subject : body.path("subjects")) {
        if (!subject.isTextual() || !candidates.contains(subject.textValue()) || !returned.add(subject.textValue())) {
          throw new Unavailable();
        }
      }
      return Set.copyOf(returned);
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      count("bookrush.product.access.discovery.errors");
      throw new Unavailable();
    }
  }

  JsonNode session(TechnicalIdentity actor, boolean invitationScope, String correlation) {
    try {
      var response = post("/api/internal/v1/product-access/session",
          java.util.Map.of("actor", actor, "invitationScope", invitationScope, "correlationId", correlation), correlation);
      if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip()
          .equalsIgnoreCase("application/json")) throw new Unavailable();
      var body = json.readTree(response.body());
      if (body == null || !correlation.equals(body.path("correlationId").textValue())) throw new Unavailable();
      return body;
    } catch (Exception failure) {
      if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new Unavailable();
    }
  }

  private HttpResponse<byte[]> post(String path, Object body, String correlation) throws Exception {
    return post(path, body, correlation, false);
  }

  private HttpResponse<byte[]> post(String path, Object body, String correlation, boolean allowMissing) throws Exception {
    var credential = workloadToken();
    var call = HttpRequest.newBuilder(config.decisionBaseUri().resolve(path))
        .timeout(config.requestTimeout())
        .header("Authorization", "Bearer " + credential.value())
        .header("X-Correlation-ID", correlation)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
    var response = send(call);
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      token.compareAndSet(credential, null);
      count("bookrush.product.access.s2s.errors");
    }
    if (response.statusCode() != 200 && !(allowMissing && response.statusCode() == 404)) throw new Unavailable();
    return response;
  }

  private boolean validate(HttpResponse<byte[]> response, String correlation) {
    try {
      if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0]
          .strip().equalsIgnoreCase("application/json")) throw new Unavailable();
      JsonNode decision = json.readTree(response.body());
      if (decision == null || !decision.isObject()) throw new Unavailable();
      for (var fields = decision.fieldNames(); fields.hasNext();) {
        if (!FIELDS.contains(fields.next())) throw new Unavailable();
      }
      var allowed = decision.path("allowed");
      if (!allowed.isBoolean() || !"1".equals(decision.path("policyVersion").textValue())
          || !correlation.equals(decision.path("correlationId").textValue())) throw new Unavailable();
      var reason = decision.path("reason");
      if (allowed.booleanValue() ? !reason.isMissingNode()
          : !reason.isTextual() || !REASONS.contains(reason.textValue())) throw new Unavailable();
      String validity = decision.path("validUntil").textValue();
      if (validity == null || !DATE_TIME.matcher(validity).matches()
          || !clock.instant().isBefore(OffsetDateTime.parse(validity).toInstant())) throw new Unavailable();
      var versions = decision.path("factVersions");
      if (!versions.isObject()) throw new Unavailable();
      for (JsonNode version : versions) ProductAccessRoutes.checkText(version.textValue());
      return allowed.booleanValue();
    } catch (Exception invalid) {
      count("bookrush.product.access.contract.errors");
      throw new Unavailable();
    }
  }

  private WorkloadToken workloadToken() throws Exception {
    var existing = token.get();
    if (existing != null && clock.instant().isBefore(existing.renewAt())) return existing;

    try {
      if (!tokenLock.tryLock(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)) throw new Unavailable();
      try {
        existing = token.get();
        if (existing != null && clock.instant().isBefore(existing.renewAt())) return existing;
        var call = HttpRequest.newBuilder(config.tokenUri()).timeout(config.requestTimeout())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=" + encode(config.clientId())
                + "&client_secret=" + encode(config.clientSecret()) + "&scope=" + encode(config.scope())))
            .build();
        var response = send(call);
        if (response.statusCode() != 200) throw new Unavailable();
        var body = json.readTree(response.body());
        String value = body.path("access_token").textValue();
        if (value == null || !"Bearer".equalsIgnoreCase(body.path("token_type").textValue())) throw new Unavailable();
        var jwt = decoder.decode(value);
        Instant expires = jwt.getExpiresAt();
        Instant now = clock.instant();
        if (expires == null || !now.isBefore(expires)
            || jwt.getAudience() == null || !jwt.getAudience().contains(config.audience())
            || !config.clientId().equals(jwt.getClaims().get("azp"))
            || !ProductAccessAuthorization.scopes(jwt.getClaims().get("scope")).contains(config.scope())) {
          throw new Unavailable();
        }
        Instant renewAt = expires.minusSeconds(10);
        var refreshed = new WorkloadToken(value, renewAt.isBefore(now) ? now : renewAt);
        token.set(refreshed);
        return refreshed;
      } finally {
        tokenLock.unlock();
      }
    } catch (Exception failure) {
      count("bookrush.product.access.s2s.errors");
      throw failure;
    }
  }

  private HttpResponse<byte[]> send(HttpRequest request) throws Exception {
    var exchange = http.sendAsync(request, info -> new LimitedBody());
    try {
      return exchange.get(config.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } finally {
      if (!exchange.isDone()) exchange.cancel(true);
    }
  }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private static final int LIMIT = 64 * 1024;
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private long received;

    @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      delegate.onSubscribe(value);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
      for (var buffer : buffers) received += buffer.remaining();
      if (received > LIMIT) {
        subscription.cancel();
        delegate.onError(new Unavailable());
      } else {
        delegate.onNext(buffers);
      }
    }
    @Override public void onError(Throwable error) { delegate.onError(error); }
    @Override public void onComplete() { delegate.onComplete(); }
  }

  private void count(String metric) {
    if (metrics != null) metrics.counter(metric).increment();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
