package com.bookrush.recommendationservice.ranking;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed.personalized.behavior")
public record BehaviorSignalsProperties(
    String url,
    String tokenUri,
    String clientId,
    String clientSecret,
    String audience,
    String scope,
    Duration cacheTtl,
    Duration connectTimeout,
    Duration readTimeout,
    int maxSignals) {
  public BehaviorSignalsProperties {
    url = url == null || url.isBlank() ? "http://behavior-service:8097" : url;
    tokenUri = tokenUri == null ? "" : tokenUri;
    clientId = clientId == null ? "" : clientId;
    clientSecret = clientSecret == null ? "" : clientSecret;
    audience = audience == null || audience.isBlank() ? "bookrush-behavior" : audience;
    scope = scope == null || scope.isBlank() ? "bookrush.behavior.read" : scope;
    cacheTtl = cacheTtl == null ? Duration.ofMinutes(2) : cacheTtl;
    connectTimeout = connectTimeout == null ? Duration.ofMillis(250) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(1) : readTimeout;
    maxSignals = maxSignals < 1 ? 50 : Math.min(maxSignals, 100);
  }

  public boolean configured() {
    return !tokenUri.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
  }
}
