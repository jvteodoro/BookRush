package com.bookrush.recommendationservice.excerpt;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Technical client. User bearers are deliberately not accepted by this class. */
public final class AnalyticsExcerptClient {
  private final RestClient analytics;
  private final RestClient tokenClient;
  private final ExcerptPolicyProperties policy;
  private final MeterRegistry metrics;
  private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
  private volatile Token token;
  private final AtomicInteger failures = new AtomicInteger();
  private volatile Instant circuitOpenUntil = Instant.MIN;

  public AnalyticsExcerptClient(RestClient.Builder builder, ExcerptPolicyProperties policy) {
    this(builder, policy, new SimpleMeterRegistry(), true);
  }

  AnalyticsExcerptClient(RestClient.Builder builder, ExcerptPolicyProperties policy, boolean configureTimeout) {
    this(builder, policy, new SimpleMeterRegistry(), configureTimeout);
  }

  public AnalyticsExcerptClient(RestClient.Builder builder, ExcerptPolicyProperties policy,
      MeterRegistry metrics) {
    this(builder, policy, metrics, true);
  }

  AnalyticsExcerptClient(RestClient.Builder builder, ExcerptPolicyProperties policy,
      MeterRegistry metrics, boolean configureTimeout) {
    this.policy = policy;
    this.metrics = metrics;
    var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(policy.connectTimeout());
    requestFactory.setReadTimeout(policy.readTimeout());
    var configured = configureTimeout ? builder.requestFactory(requestFactory) : builder;
    this.analytics = configured.baseUrl(policy.analyticsUrl()).build();
    this.tokenClient = (configureTimeout ? builder.requestFactory(requestFactory) : builder).build();
  }

  @SuppressWarnings("unchecked")
  public FeedExcerpt find(UUID bookId) {
    if (!policy.enabled() || policy.percentage() == 0 || !policy.configured()) return null;
    if (circuitOpenUntil.isAfter(Instant.now())) return null;
    CacheKey cacheKey = new CacheKey(bookId, policyKey());
    CacheEntry cached = cache.get(cacheKey);
    if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
      metrics.counter("bookrush.feed.excerpt.cache", "result", "hit").increment();
      return cached.value();
    }
    metrics.counter("bookrush.feed.excerpt.cache", "result", "miss").increment();
    try {
      String bearer = accessToken();
      Map<String, Object> body = analytics.get()
          .uri(uri -> uri.path("/api/internal/v1/content-analytics/books/{bookId}/excerpts")
              .queryParam("offset", 0).queryParam("limit", 50).build(bookId))
          .headers(headers -> headers.setBearerAuth(bearer))
          .retrieve().body(Map.class);
      List<Map<String, Object>> rows = body != null && body.get("items") instanceof List<?> list
          ? list.stream().filter(Map.class::isInstance).map(v -> (Map<String, Object>) v).toList() : List.of();
      FeedExcerpt selected = ExcerptSelection.select(rows, policy);
      cache.put(cacheKey, new CacheEntry(selected,
          Instant.now().plus(selected == null ? policy.negativeTtl() : policy.positiveTtl())));
      failures.set(0);
      circuitOpenUntil = Instant.MIN;
      return selected;
    } catch (RestClientException | IllegalStateException ex) {
      failures.incrementAndGet();
      cache.put(cacheKey, new CacheEntry(null, Instant.now().plus(policy.negativeTtl())));
      if (failures.get() >= 3) circuitOpenUntil = Instant.now().plusSeconds(10);
      return null;
    }
  }

  private String accessToken() {
    Token current = token;
    if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(10))) return current.value();
    synchronized (this) {
      current = token;
      if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(10))) return current.value();
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "client_credentials");
      form.add("client_id", policy.clientId());
      form.add("client_secret", policy.clientSecret());
      form.add("scope", policy.scope());
      form.add("audience", policy.audience());
      Map<?, ?> response = tokenClient.post().uri(policy.tokenUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form).retrieve().body(Map.class);
      if (response == null || response.get("access_token") == null) throw new IllegalStateException("S2S token unavailable");
      long expires = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
      token = new Token(String.valueOf(response.get("access_token")), Instant.now().plusSeconds(Math.max(1, expires)));
      return token.value();
    }
  }

  public int failureCount() { return failures.get(); }
  public void invalidate() { cache.clear(); token = null; circuitOpenUntil = Instant.MIN; failures.set(0); }
  private String policyKey() {
    return policy.rankerVersion() + ":" + policy.maxTextCodepoints() + ":" + policy.requireBodyEligible();
  }
  private record Token(String value, Instant expiresAt) {}
  private record CacheKey(UUID bookId, String policyHash) {}
  private record CacheEntry(FeedExcerpt value, Instant expiresAt) {}
}
