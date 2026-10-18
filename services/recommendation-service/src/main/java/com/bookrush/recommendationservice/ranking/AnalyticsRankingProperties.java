package com.bookrush.recommendationservice.ranking;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed.ranking")
public record AnalyticsRankingProperties(
    boolean enabled,
    String modelVersion,
    String analyticsUrl,
    String tokenUri,
    String clientId,
    String clientSecret,
    String audience,
    String scope,
    Duration cacheTtl,
    Duration connectTimeout,
    Duration readTimeout,
    int maxCandidates) {
  public AnalyticsRankingProperties {
    modelVersion = modelVersion == null || modelVersion.isBlank()
        ? "analytics-aware-v1" : modelVersion;
    analyticsUrl = analyticsUrl == null || analyticsUrl.isBlank()
        ? "http://book-analytics-service:8091" : analyticsUrl;
    tokenUri = tokenUri == null ? "" : tokenUri;
    clientId = clientId == null ? "" : clientId;
    clientSecret = clientSecret == null ? "" : clientSecret;
    audience = audience == null || audience.isBlank() ? "bookrush-analytics" : audience;
    scope = scope == null || scope.isBlank() ? "bookrush.analytics" : scope;
    cacheTtl = cacheTtl == null ? Duration.ofMinutes(10) : cacheTtl;
    connectTimeout = connectTimeout == null ? Duration.ofMillis(250) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofMillis(700) : readTimeout;
    maxCandidates = maxCandidates < 1 ? 100 : Math.min(maxCandidates, 100);
  }

  public boolean configured() {
    return !tokenUri.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
  }
}
