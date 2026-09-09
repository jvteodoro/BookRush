package com.bookrush.catalog.canonical;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "canonical.service")
public record CanonicalServiceProperties(String token) {
  public CanonicalServiceProperties { token = token == null ? "" : token; }
}
