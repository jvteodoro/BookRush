package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class SubscriptionEntitlements {
  private record Token(String access_token) {}
  private record EvaluationRequest(TechnicalIdentity owner, String entitlement) {}
  record Evaluation(Boolean enabled, Instant effectiveUntil, Long version) {}

  private final RestClient http;
  private final String tokenUrl;
  private final String endpoint;
  private final String clientId;
  private final String secret;
  private final Clock clock;
  private final MeterRegistry metrics;

  SubscriptionEntitlements(@Value("${family.token-url}") String tokenUrl,
      @Value("${family.subscription-url}") String subscription, @Value("${family.client-id}") String clientId,
      @Value("${family.client-secret}") String secret, Clock clock, MeterRegistry metrics) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(2));
    this.http = RestClient.builder().requestFactory(factory).build();
    this.tokenUrl = tokenUrl;
    this.endpoint = subscription + "/api/internal/v1/subscription/entitlements/evaluate";
    this.clientId = clientId;
    this.secret = secret;
    this.clock = clock;
    this.metrics = metrics;
  }

  Instant requirePremium(TechnicalIdentity owner) {
    Evaluation rights;
    try {
      if (secret.isBlank()) {
        throw new RestClientException("workload credential unavailable");
      }
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "client_credentials");
      form.add("scope", "entitlements.evaluate");
      Token token = http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .headers(headers -> headers.setBasicAuth(clientId, secret)).body(form).retrieve().body(Token.class);
      if (token == null || token.access_token() == null || token.access_token().isBlank()) {
        throw new RestClientException("workload token unavailable");
      }
      rights = http.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> headers.setBearerAuth(token.access_token()))
          .body(new EvaluationRequest(owner, "PREMIUM_FAMILY")).retrieve().body(Evaluation.class);
      if (rights == null || rights.enabled() == null || rights.version() == null || rights.version() < 0
          || (rights.enabled() && rights.effectiveUntil() == null)) {
        throw new RestClientException("invalid entitlement response");
      }
    } catch (RestClientException failure) {
      metrics.counter("bookrush.family.subscription.failures").increment();
      throw FamilyProblem.unavailable();
    }
    checkDeadline(rights.enabled() ? rights.effectiveUntil() : null);
    return rights.effectiveUntil();
  }

  void checkDeadline(Instant until) {
    if (until == null || !clock.instant().isBefore(until)) {
      throw FamilyProblem.forbidden("ENTITLEMENT_REQUIRED");
    }
  }
}
