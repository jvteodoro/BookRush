package com.bookrush.recommendationservice.ranking;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Version-aware local projection of the analytics recommendation snapshot. */
public final class AnalyticsScoreClient {
  private final RestClient analytics;
  private final RestClient tokenClient;
  private final AnalyticsRankingProperties properties;
  private final MeterRegistry metrics;
  private final AtomicReference<Map<CacheKey, CacheEntry>> cache = new AtomicReference<>(Map.of());
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

  public Map<UUID, BookScore> find(List<UUID> bookIds) {
    var snapshots = findSnapshots(bookIds);
    var scores = new HashMap<UUID, BookScore>();
    snapshots.forEach((id, snapshot) -> {
      if (snapshot.score() != null && Double.isFinite(snapshot.score())) {
        scores.put(id, new BookScore(snapshot.score(), snapshot.rankerVersion()));
      }
    });
    return scores;
  }

  @SuppressWarnings("unchecked")
  public Map<UUID, BookFeatureSnapshot> findSnapshots(List<UUID> bookIds) {
    return findSnapshots(bookIds, false);
  }

  public Map<UUID, BookFeatureSnapshot> findSnapshots(List<UUID> bookIds, boolean includeEmbedding) {
    if (!properties.enabled() || !properties.configured() || bookIds.isEmpty()) return Map.of();
    var bounded = bookIds.stream().distinct().limit(properties.maxCandidates()).toList();
    var current = cache.get();
    var result = new HashMap<UUID, BookFeatureSnapshot>();
    var missing = new java.util.ArrayList<UUID>();
    var stale = new HashMap<UUID, BookFeatureSnapshot>();
    Instant now = Instant.now();
    for (UUID id : bounded) {
      CacheEntry entry = current.entrySet().stream()
          .filter(candidate -> candidate.getKey().bookId().equals(id))
          .map(Map.Entry::getValue)
          .findFirst().orElse(null);
      if (entry != null && entry.expiresAt().isAfter(now)
          && (!includeEmbedding || (entry.snapshot().embedding() != null
              && entry.snapshot().embedding().vector() != null))) result.put(id, entry.snapshot());
      else {
        missing.add(id);
        if (entry != null) stale.put(id, entry.snapshot());
      }
    }
    metrics.counter("bookrush.feed.book_features.cache", "outcome", "hit")
        .increment(bounded.size() - missing.size());
    metrics.counter("bookrush.feed.book_features.cache", "outcome", "miss")
        .increment(missing.size());
    if (missing.isEmpty()) {
      metrics.counter("bookrush.feed.ranking", "result", "cache_hit").increment();
      return result;
    }
    long started = System.nanoTime();
    try {
      String bearer = accessToken();
      var requestBody = new java.util.LinkedHashMap<String, Object>();
      requestBody.put("bookIds", missing);
      requestBody.put("offset", 0);
      requestBody.put("limit", missing.size());
      if (includeEmbedding) requestBody.put("includeEmbedding", true);
      Map<String, Object> body = analytics.post()
          .uri("/api/internal/v1/content-analytics/recommendation-snapshot")
          .contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> headers.setBearerAuth(bearer))
          .body(requestBody)
          .retrieve().body(Map.class);
      Object rawItems = body == null ? null : body.get("items");
      var updates = new HashMap<CacheKey, CacheEntry>();
      if (rawItems instanceof List<?> rows) {
        for (Object value : rows) {
          if (!(value instanceof Map<?, ?> row) || row.get("bookId") == null) continue;
          try {
            UUID id = UUID.fromString(String.valueOf(row.get("bookId")));
            var snapshot = snapshot(id, row);
            String embeddingModel = snapshot.embedding() == null ? "" : snapshot.embedding().model();
            updates.put(new CacheKey(id, snapshot.sourceAssetVersionId(), embeddingModel),
                new CacheEntry(snapshot, now.plus(properties.cacheTtl())));
            result.put(id, snapshot);
          } catch (IllegalArgumentException ignored) {
            // One malformed row cannot poison the projection or the feed.
          }
        }
      }
      // Swap once, after parsing the batch, so readers never observe a partial refresh.
      if (!updates.isEmpty()) {
        var replacement = new HashMap<>(cache.get());
        replacement.keySet().removeIf(key -> updates.keySet().stream()
            .anyMatch(updated -> updated.bookId().equals(key.bookId())));
        replacement.putAll(updates);
        cache.set(Map.copyOf(replacement));
      }
      metrics.counter("bookrush.feed.book_features", "outcome", "returned").increment(result.size());
      metrics.counter("bookrush.feed.book_features", "outcome", "with_embedding")
          .increment(result.values().stream().filter(snapshot -> snapshot.embedding() != null).count());
      metrics.counter("bookrush.feed.ranking", "result", "analytics").increment();
      return result;
    } catch (RestClientException | IllegalStateException ex) {
      // A stale snapshot is safer than mixing a new request with a zero/default vector.
      result.putAll(stale);
      metrics.counter("bookrush.feed.book_features", "outcome", "stale_on_error")
          .increment(stale.size());
      metrics.counter("bookrush.feed.analytics_scores", "outcome",
          ex instanceof org.springframework.web.client.ResourceAccessException ? "timeout" : "error").increment();
      metrics.counter("bookrush.feed.ranking", "result", "fallback").increment();
      return result;
    } finally {
      metrics.timer("bookrush.feed.analytics_scores.duration")
          .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

  private BookFeatureSnapshot snapshot(UUID id, Map<?, ?> row) {
    UUID version = uuid(row.get("sourceAssetVersionId"));
    Double score = row.get("score") instanceof Number n ? n.doubleValue() : null;
    Map<?, ?> embeddingRow = row.get("embedding") instanceof Map<?, ?> value ? value : null;
    EmbeddingMetadata embedding = embeddingRow == null ? null : new EmbeddingMetadata(
        string(embeddingRow.get("model")), string(embeddingRow.get("modelVersion")),
        embeddingRow.get("dimension") instanceof Number n ? n.intValue() : 0,
        string(embeddingRow.get("inputSha256")), uuid(embeddingRow.get("sourceAssetVersionId")),
        string(embeddingRow.get("storageProvider")), string(embeddingRow.get("bucket")),
        string(embeddingRow.get("objectKey")), vector(embeddingRow.get("vector")));
    return new BookFeatureSnapshot(id, version, score, string(row.get("rankerCode")),
        string(row.get("rankerVersion")), string(row.get("rankConfigurationHash")), embedding);
  }

  private static UUID uuid(Object value) {
    if (value == null) return null;
    try { return UUID.fromString(String.valueOf(value)); }
    catch (IllegalArgumentException ignored) { return null; }
  }

  private static String string(Object value) { return value == null ? null : String.valueOf(value); }

  private static List<Double> vector(Object value) {
    if (!(value instanceof List<?> values)) return null;
    var result = new java.util.ArrayList<Double>(values.size());
    for (Object item : values) {
      if (!(item instanceof Number number) || !Double.isFinite(number.doubleValue())) return null;
      result.add(number.doubleValue());
    }
    return result.isEmpty() ? null : List.copyOf(result);
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

  public record BookFeatureSnapshot(UUID bookId, UUID sourceAssetVersionId, Double score,
      String rankerCode, String rankerVersion, String rankConfigurationHash,
      EmbeddingMetadata embedding) { }

  public record EmbeddingMetadata(String model, String modelVersion, int dimension,
      String inputSha256, UUID sourceAssetVersionId, String storageProvider,
      String bucket, String objectKey, List<Double> vector) { }

  private record Token(String value, Instant expiresAt) { }
  private record CacheKey(UUID bookId, UUID sourceAssetVersionId, String embeddingModel) { }
  private record CacheEntry(BookFeatureSnapshot snapshot, Instant expiresAt) { }
}
