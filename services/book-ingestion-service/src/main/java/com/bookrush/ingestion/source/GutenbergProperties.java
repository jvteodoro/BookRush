package com.bookrush.ingestion.source;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Endpoint root for Gutenberg acquisition; overridden by the offline harness. */
@ConfigurationProperties(prefix = "ingestion.gutenberg")
public record GutenbergProperties(String baseUrl, boolean allowPrivateAddresses, String catalogUrl) {
  public GutenbergProperties(String baseUrl, boolean allowPrivateAddresses) {
    this(baseUrl, allowPrivateAddresses, "https://gutendex.com/books/");
  }
  @ConstructorBinding
  public GutenbergProperties {
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://www.gutenberg.org" : baseUrl.replaceAll("/+$", "");
    catalogUrl = catalogUrl == null || catalogUrl.isBlank() ? "https://gutendex.com/books/" : catalogUrl;
    var uri = URI.create(baseUrl);
    var catalog = URI.create(catalogUrl);
    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
        || (!"http".equalsIgnoreCase(catalog.getScheme()) && !"https".equalsIgnoreCase(catalog.getScheme())) || catalog.getHost() == null) {
      throw new IllegalArgumentException("ingestion.gutenberg.base-url must be an HTTP(S) URL");
    }
  }
}
