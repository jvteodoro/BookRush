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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded S2S client for behavior-owned serving signals. */
public final class BehaviorSignalsClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorSignalsClient.class);
  private final RestClient behavior;
  private final RestClient tokenClient;
  private final BehaviorSignalsProperties properties;
  private final MeterRegistry metrics;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();
  private volatile Token token;

  public BehaviorSignalsClient(RestClient.Builder builder, BehaviorSignalsProperties properties,
      MeterRegistry metrics) {
    this(builder, properties, metrics, true);
  }

  BehaviorSignalsClient(RestClient.Builder builder, BehaviorSignalsProperties properties,
      MeterRegistry metrics, boolean configureTimeout) {
    this.properties = properties;
    this.metrics = metrics;
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    var configured = configureTimeout ? builder.requestFactory(factory) : builder;
    this.behavior = configured.baseUrl(properties.url()).build();
    this.tokenClient = (configureTimeout ? builder.requestFactory(factory) : builder).build();
  }

  public List<Signal> find(String subject) {
    return lookup(subject).signals();
  }

  public Lookup lookup(String subject) {
    if (!properties.configured() || subject == null || subject.isBlank()) {
      return new Lookup(List.of(), false);
    }
    var cached = cache.get(subject);
    if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
      metrics.counter("bookrush.feed.personalized.behavior", "outcome", "cache_hit").increment();
      return new Lookup(cached.signals(), cached.available());
    }
    long started = System.nanoTime();
    try {
      String bearer = accessToken();
      Map<String, Object> body = behavior.post()
          .uri("/api/internal/v1/recommendation/signals")
          .contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> headers.setBearerAuth(bearer))
          .body(Map.of("subject", subject, "limit", properties.maxSignals(), "offset", 0))
          .retrieve().body(Map.class);
      var result = parse(body);
      cache.put(subject, new Entry(subject, result, true, Instant.now().plus(properties.cacheTtl())));
      metrics.counter("bookrush.feed.personalized.behavior", "outcome", "success").increment();
      return new Lookup(result, true);
    } catch (RestClientException | IllegalStateException exception) {
      metrics.counter("bookrush.feed.personalized.behavior", "outcome",
          exception instanceof org.springframework.web.client.ResourceAccessException ? "timeout" : "error").increment();
      LOGGER.warn("Behavior serving signals unavailable: outcome={}",
          exception instanceof org.springframework.web.client.ResourceAccessException ? "timeout" : "error");
      return new Lookup(List.of(), false);
    } finally {
      metrics.timer("bookrush.feed.personalized.behavior.duration")
          .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

  private List<Signal> parse(Map<String, Object> body) {
    if (body == null || !(body.get("items") instanceof List<?> items)) return List.of();
    return items.stream().filter(Map.class::isInstance).map(value -> signal((Map<?, ?>) value))
        .filter(java.util.Objects::nonNull).limit(properties.maxSignals()).toList();
  }

  private Signal signal(Map<?, ?> row) {
    try {
      Object id = row.get("bookId");
      if (id == null) return null;
      return new Signal(UUID.fromString(String.valueOf(id)), number(row.get("impressionCount")),
          number(row.get("openCount")), number(row.get("completionCount")), number(row.get("activeSeconds")),
          decimal(row.get("maxProgress")), bool(row.get("liked")), bool(row.get("saved")),
          instant(row.get("lastEventAt")));
    } catch (IllegalArgumentException exception) {
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
      form.add("client_id", properties.clientId());
      form.add("client_secret", properties.clientSecret());
      form.add("scope", properties.scope());
      form.add("audience", properties.audience());
      Map<?, ?> response = tokenClient.post().uri(properties.tokenUri())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
      if (response == null || response.get("access_token") == null)
        throw new IllegalStateException("behavior S2S token unavailable");
      long expires = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
      token = new Token(String.valueOf(response.get("access_token")), Instant.now().plusSeconds(Math.max(1, expires)));
      return token.value();
    }
  }

  private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
  private static double decimal(Object value) { return value instanceof Number n ? n.doubleValue() : 0; }
  private static boolean bool(Object value) { return Boolean.TRUE.equals(value); }
  private static Instant instant(Object value) { return value == null ? null : Instant.parse(String.valueOf(value)); }

  public record Signal(UUID bookId, long impressionCount, long openCount, long completionCount,
      long activeSeconds, double maxProgress, boolean liked, boolean saved, Instant lastEventAt) {
    public double positiveWeight() {
      double weight = 0;
      if (liked) weight += 3;
      if (saved) weight += 2.5;
      weight += Math.min(1, completionCount) * 2;
      weight += Math.min(1, openCount) * .5;
      weight += Math.min(1, Math.max(0, activeSeconds) / 1800d);
      weight += Math.min(1, Math.max(0, maxProgress) / 100d) * 1.5;
      return weight;
    }
  }

  public record Lookup(List<Signal> signals, boolean available) {
    public Lookup {
      signals = signals == null ? List.of() : List.copyOf(signals);
    }
  }

  private record Entry(String subject, List<Signal> signals, boolean available, Instant expiresAt) { }
  private record Token(String value, Instant expiresAt) { }
}
