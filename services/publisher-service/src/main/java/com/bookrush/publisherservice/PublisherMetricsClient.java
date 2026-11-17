package com.bookrush.publisherservice;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Dedicated read-only identity; never forwards a publisher's bearer to behavior. */
@Service
public class PublisherMetricsClient {
  private final RestClient client;
  private final String tokenUri, clientId, clientSecret;
  private volatile String token;
  private volatile Instant expiresAt = Instant.EPOCH;
  public PublisherMetricsClient(
      @Value("${publisher.behavior-url:http://behavior-service:8097}") String url,
      @Value("${publisher.metrics.token-uri:}") String tokenUri,
      @Value("${publisher.metrics.client-id:bookrush-publisher-metrics-s2s}") String clientId,
      @Value("${publisher.metrics.client-secret:}") String clientSecret) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
    this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    this.tokenUri = tokenUri; this.clientId = clientId; this.clientSecret = clientSecret;
  }
  public List<Map<String, Object>> metrics(UUID bookId, int days) {
    try {
      return client.get().uri("/api/internal/v1/publisher/metrics/books/{id}?days={days}", bookId, days)
          .headers(h -> h.setBearerAuth(accessToken())).retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {});
    } catch (Exception failure) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Publication metrics are temporarily unavailable");
    }
  }
  private synchronized String accessToken() {
    if (token != null && expiresAt.isAfter(Instant.now().plusSeconds(10))) return token;
    if (tokenUri.isBlank() || clientSecret.isBlank()) throw new IllegalStateException("metrics credential missing");
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials"); form.add("client_id", clientId);
    form.add("client_secret", clientSecret); form.add("scope", "bookrush.publisher.metrics.read");
    var response = client.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form).retrieve().body(Map.class);
    if (response == null || !(response.get("access_token") instanceof String value) || value.isBlank())
      throw new IllegalStateException("metrics token unavailable");
    token = value;
    expiresAt = Instant.now().plusSeconds(response.get("expires_in") instanceof Number n ? Math.max(1,n.longValue()) : 60);
    return token;
  }
}
