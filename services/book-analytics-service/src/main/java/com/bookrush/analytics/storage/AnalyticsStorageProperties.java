package com.bookrush.analytics.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics.storage")
public record AnalyticsStorageProperties(
    URI endpoint,
    String region,
    String accessKey,
    String secretKey,
    boolean pathStyle,
    int timeoutSeconds) {}
