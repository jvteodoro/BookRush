package com.bookrush.recommendationservice.ranking;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Batch S2S client for product ranking scores; failures always degrade to heuristic-v1. */
public final class AnalyticsScoreClient {
  private final RestClient analytics;
  private final RestClient tokenClient;
  private final AnalyticsRankingProperties properties;
  private final MeterRegistry metrics;
  private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
  private volatile Token token;

  public AnalyticsScoreClient(RestClient.Builder builder, AnalyticsRankingProperties properties,
      MeterRegistry metrics) {
    this(builder, properties, metrics, true);
  }

  AnalyticsScoreClient(RestClient.Builder builder, AnalyticsRankingProperties properties,
      MeterRegistry metrics, boolean configureTimeout) {
    this.properties = properties;
    this.metrics = metrics;
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    var configured = configureTimeout ? builder.requestFactory(factory) : builder;
    this.analytics = configured.baseUrl(properties.analyticsUrl()).build();
    this.tokenClient = (configureTimeout ? builder.requestFactory(factory) : builder).build();
  }

  @SuppressWarnings("unchecked")
  public Map<UUID, BookScore> find(List<UUID> bookIds) {
    if (!properties.enabled() || !properties.configured() || bookIds.isEmpty()) return Map.of();
    var bounded = bookIds.stream().distinct().limit(properties.maxCandidates()).toList();
    var result = new java.util.HashMap<UUID, BookScore>();
    var missing = new java.util.ArrayList<UUID>();
    Instant now = Instant.now();
    for (UUID id : bounded) {
      CacheEntry entry = cache.get(id);
      if (entry != null && entry.expiresAt().isAfter(now)) result.put(id, entry.score());
      else missing.add(id);
    }
    if (missing.isEmpty()) {
      metrics.counter("bookrush.feed.ranking", "result", "cache_hit").increment();
      return result;
    }
    try {
      String bearer = accessToken();
      Map<String, Object> body = analytics.post()
          .uri("/api/internal/v1/content-analytics/book-scores")
          .contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> headers.setBearerAuth(bearer))
          .body(Map.of("bookIds", missing))
          .retrieve().body(Map.class);
      Object items = body == null ? null : body.get("items");
      if (items instanceof List<?> rows) {
        for (Object value : rows) {
          if (!(value instanceof Map<?, ?> row) || row.get("bookId") == null
              || !(row.get("score") instanceof Number score) || !Double.isFinite(score.doubleValue())) continue;
          try {
            UUID id = UUID.fromString(String.valueOf(row.get("bookId")));
            var entry = new CacheEntry(new BookScore(score.doubleValue(),
                row.get("rankerVersion") == null ? null : String.valueOf(row.get("rankerVersion"))),
                now.plus(properties.cacheTtl()));
            cache.put(id, entry);
            result.put(id, entry.score());
          } catch (IllegalArgumentException ignored) { }
        }
      }
      for (UUID id : missing) cache.putIfAbsent(id,
          new CacheEntry(result.get(id), now.plus(properties.cacheTtl())));
      metrics.counter("bookrush.feed.ranking", "result", "analytics").increment();
      return result;
    } catch (RestClientException | IllegalStateException ex) {
      metrics.counter("bookrush.feed.ranking", "result", "fallback").increment();
      return result;
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
      form.add("client_id", properties.clientId());
      form.add("client_secret", properties.clientSecret());
      form.add("scope", properties.scope());
      form.add("audience", properties.audience());
      Map<?, ?> response = tokenClient.post().uri(properties.tokenUri())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
      if (response == null || response.get("access_token") == null)
        throw new IllegalStateException("S2S analytics ranking token unavailable");
      long expires = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
      token = new Token(String.valueOf(response.get("access_token")),
          Instant.now().plusSeconds(Math.max(1, expires)));
      return token.value();
    }
  }

  public record BookScore(double score, String rankerVersion) { }
  private record Token(String value, Instant expiresAt) { }
  private record CacheEntry(BookScore score, Instant expiresAt) { }
}
