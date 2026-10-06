package com.bookrush.recommendationservice.excerpt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed.excerpts")
public record ExcerptPolicyProperties(
    boolean enabled,
    int percentage,
    String rankerVersion,
    int maxTextCodepoints,
    int maxExcerptsPerBook,
    boolean requireBodyEligible,
    Duration positiveTtl,
    Duration negativeTtl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration totalBudget,
    int maxConcurrency,
    String analyticsUrl,
    String tokenUri,
    String clientId,
    String clientSecret,
    String audience,
    String scope) {
  public ExcerptPolicyProperties {
    percentage = Math.max(0, Math.min(100, percentage));
    maxTextCodepoints = maxTextCodepoints < 1 ? 1600 : maxTextCodepoints;
    maxExcerptsPerBook = maxExcerptsPerBook < 1 ? 1 : Math.min(1, maxExcerptsPerBook);
    positiveTtl = positiveTtl == null ? Duration.ofMinutes(15) : positiveTtl;
    negativeTtl = negativeTtl == null ? Duration.ofMinutes(2) : negativeTtl;
    connectTimeout = connectTimeout == null ? Duration.ofMillis(250) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofMillis(500) : readTimeout;
    totalBudget = totalBudget == null ? Duration.ofMillis(900) : totalBudget;
    maxConcurrency = maxConcurrency < 1 ? 4 : Math.min(maxConcurrency, 16);
    analyticsUrl = analyticsUrl == null ? "http://book-analytics-service:8091" : analyticsUrl;
    tokenUri = tokenUri == null ? "" : tokenUri;
    clientId = clientId == null ? "" : clientId;
    clientSecret = clientSecret == null ? "" : clientSecret;
    audience = audience == null ? "bookrush-analytics" : audience;
    scope = scope == null ? "bookrush.analytics" : scope;
  }

  public boolean configured() {
    return !tokenUri.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
  }
}
