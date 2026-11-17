package com.bookrush.publisherservice;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Client-credentials access to behavior-owned, thresholded publisher metrics. */
@Component
public final class PublisherBehaviorMetricsClient {
  private final RestClient behavior;
  private final RestClient tokenClient;
  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;
  private final String scope;
  private final String audience;
  private volatile CachedToken cachedToken;

  public PublisherBehaviorMetricsClient(RestClient.Builder builder,
      @Value("${publisher.behavior-url:http://behavior-service:8097}") String behaviorUrl,
      @Value("${publisher.behavior-metrics.token-uri:http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token}") String tokenUri,
      @Value("${publisher.behavior-metrics.client-id:bookrush-publisher-behavior-s2s}") String clientId,
      @Value("${publisher.behavior-metrics.client-secret:}") String clientSecret,
      @Value("${publisher.behavior-metrics.scope:bookrush.publisher.metrics.read}") String scope,
      @Value("${publisher.behavior-metrics.audience:bookrush-behavior}") String audience) {
    var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(500));
    requestFactory.setReadTimeout(Duration.ofSeconds(2));
    this.behavior = builder.clone().requestFactory(requestFactory).baseUrl(behaviorUrl).build();
    this.tokenClient = builder.clone().requestFactory(requestFactory).build();
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
    this.audience = audience;
  }

  public Object bookMetrics(Object bookId) {
    return behavior.get()
        .uri(uri -> uri.path("/api/internal/v1/publisher/metrics/books/{bookId}")
            .queryParam("days", 366).build(bookId))
        .headers(headers -> headers.setBearerAuth(accessToken()))
        .retrieve().body(Map.class);
  }

  private String accessToken() {
    CachedToken current = cachedToken;
    if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(15)))
      return current.value();
    synchronized (this) {
      current = cachedToken;
      if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(15)))
        return current.value();
      if (clientSecret == null || clientSecret.isBlank())
        throw new IllegalStateException("publisher behavior S2S credentials are not configured");
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "client_credentials");
      form.add("client_id", clientId);
      form.add("client_secret", clientSecret);
      form.add("scope", scope);
      form.add("audience", audience);
      Map<?, ?> response = tokenClient.post().uri(tokenUri)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
          .retrieve().body(Map.class);
      if (response == null || response.get("access_token") == null)
        throw new IllegalStateException("publisher behavior S2S token unavailable");
      long expires = response.get("expires_in") instanceof Number value ? value.longValue() : 300;
      cachedToken = new CachedToken(String.valueOf(response.get("access_token")),
          Instant.now().plusSeconds(Math.max(30, expires)));
      return cachedToken.value();
    }
  }

  private record CachedToken(String value, Instant expiresAt) {}
}
