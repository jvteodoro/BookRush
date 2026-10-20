package com.bookrush.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics.runtime")
public record AnalyticsRuntimeProperties(String url, int timeoutSeconds, int languageSampleChars) {
  public AnalyticsRuntimeProperties {
    if (url == null || url.isBlank()) url = "http://book-analytics-runtime:8092";
    if (timeoutSeconds < 1) timeoutSeconds = 10;
    if (languageSampleChars < 1) languageSampleChars = 90_000;
  }
}
