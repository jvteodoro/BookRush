package com.bookrush.family;

import com.bookrush.platform.core.AgePolicy;
import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

@Component
class KeycloakAgeFacts implements AgePolicy.Source {
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Token(String access_token) {}

  record Account(boolean enabled, AgePolicy.Decision age) {
    boolean is(AgePolicy.State state) {
      return enabled && age.state() == state && age.meetsEvidenceRequirement(false);
    }
  }

  private final RestClient http;
  private final String issuer;
  private final String adminUsers;
  private final String tokenUrl;
  private final String secret;
  private final Clock clock;
  private final MeterRegistry metrics;

  KeycloakAgeFacts(@Value("${bookrush.security.issuer}") String issuer,
      @Value("${family.identity-admin-users}") String adminUsers,
      @Value("${family.identity-token-url}") String tokenUrl,
      @Value("${family.identity-secret}") String secret, Clock clock, MeterRegistry metrics) {
    this.issuer = issuer;
    this.adminUsers = adminUsers;
    this.tokenUrl = tokenUrl;
    this.secret = secret;
    this.clock = clock;
    this.metrics = metrics;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(2));
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public AgePolicy.Decision classify(TechnicalIdentity identity) {
    return read(identity).age();
  }

  Account read(TechnicalIdentity identity) {
    if (!issuer.equals(identity.issuer())) {
      throw FamilyProblem.missing("RESOURCE_NOT_FOUND");
    }
    if (secret.isBlank() || adminUsers.isBlank() || tokenUrl.isBlank()) {
      throw unavailable();
    }
    try {
      String token = accessToken();
      JsonNode user;
      try {
        user = http.get().uri(adminUsers + "/{subject}", identity.subject())
            .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
      } catch (HttpClientErrorException.NotFound absent) {
        return new Account(false, AgePolicy.classify(List.of(), AgePolicy.Provenance.NONE, clock));
      }
      if (user == null || !identity.subject().equals(user.path("id").textValue()) || !user.path("enabled").isBoolean()) {
        throw unavailable();
      }
      var attributes = user.path("attributes");
      if (!attributes.isMissingNode() && !attributes.isNull() && !attributes.isObject()) throw unavailable();
      var birthdate = attributes.path("birthdate");
      var dates = new ArrayList<String>();
      if (!birthdate.isMissingNode()) {
        if (!birthdate.isArray()) throw unavailable();
        for (var value : birthdate) {
          if (!value.isTextual()) throw unavailable();
          dates.add(value.textValue());
        }
      }
      return new Account(user.path("enabled").booleanValue(),
          AgePolicy.classify(dates, AgePolicy.Provenance.DECLARED, clock));
    } catch (RestClientException failure) {
      throw unavailable();
    }
  }

  private String accessToken() {
    if (secret.isBlank() || adminUsers.isBlank() || tokenUrl.isBlank()) throw unavailable();
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    var token = http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .headers(h -> h.setBasicAuth("bookrush-family-age-s2s", secret))
        .body(form).retrieve().body(Token.class);
    if (token == null || token.access_token() == null || token.access_token().isBlank()) {
      throw unavailable();
    }
    return token.access_token();
  }

  java.util.Set<String> discoverableProfiles(java.util.Set<String> candidates, boolean verifiedRequired) {
    if (candidates.isEmpty()) return java.util.Set.of();
    try {
      String token = accessToken();
      var adults = new java.util.HashSet<String>();
      // Single bounded roster read. Identities outside the roster are withheld rather than inferred.
      JsonNode accounts = http.get().uri(adminUsers + "?enabled=true&briefRepresentation=false&first=0&max=500")
          .headers(h -> h.setBearerAuth(token)).retrieve().body(JsonNode.class);
      if (accounts == null || !accounts.isArray() || accounts.size() > 500) throw unavailable();
      for (JsonNode account : accounts) {
        String subject = account.path("id").textValue();
        if (subject == null || !account.path("enabled").isBoolean()) throw unavailable();
        if (!candidates.contains(subject) || !account.path("enabled").booleanValue()) continue;
        var date = account.path("attributes").path("birthdate");
        if (!date.isArray() || date.size() != 1 || !date.get(0).isTextual()) continue;
        var age = AgePolicy.classify(List.of(date.get(0).textValue()), AgePolicy.Provenance.DECLARED, clock);
        if (age.state() == AgePolicy.State.ADULT && age.meetsEvidenceRequirement(verifiedRequired)) {
          adults.add(subject);
        }
      }
      return java.util.Set.copyOf(adults);
    } catch (RestClientException failure) {
      throw unavailable();
    }
  }

  private FamilyProblem unavailable() {
    metrics.counter("bookrush.family.supervision.evaluation.failures", "dependency", "identity").increment();
    return FamilyProblem.unavailable();
  }
}
