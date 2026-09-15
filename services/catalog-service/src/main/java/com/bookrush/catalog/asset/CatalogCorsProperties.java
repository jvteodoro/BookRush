package com.bookrush.catalog.asset;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit browser origins allowed to call the catalog's internal read API. */
@ConfigurationProperties(prefix = "catalog.security.cors")
public record CatalogCorsProperties(List<String> allowedOrigins) {
  public CatalogCorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
