package com.bookrush.ingestion.source;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Endpoint root for Gutenberg acquisition; overridden by the offline harness. */
@ConfigurationProperties(prefix = "ingestion.gutenberg")
public record GutenbergProperties(String baseUrl, boolean allowPrivateAddresses) {
  public GutenbergProperties {
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://www.gutenberg.org" : baseUrl.replaceAll("/+$", "");
    var uri = URI.create(baseUrl);
    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw new IllegalArgumentException("ingestion.gutenberg.base-url must be an HTTP(S) URL");
    }
  }
}
