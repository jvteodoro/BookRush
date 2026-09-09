package com.bookrush.ingestion.catalog;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@EnableConfigurationProperties(CanonicalCatalogProperties.class)
public final class HttpCanonicalCatalogAdapter implements CanonicalCatalogPort {
  private final RestClient client;
  private final CanonicalCatalogProperties properties;

  public HttpCanonicalCatalogAdapter(RestClient.Builder builder, CanonicalCatalogProperties properties) {
    this.properties = properties;
    this.client = builder.baseUrl(properties.baseUrl().toString()).build();
  }

  @Override
  public Map<String, Object> apply(CanonicalCatalogCommand command) {
    try {
      return client.post().uri("/api/internal/v1/catalog/commands")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceToken())
          .body(command).retrieve().body(Map.class);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 409) throw new CanonicalConflict(e.getResponseBodyAsString());
      throw e;
    }
  }

  public static final class CanonicalConflict extends RuntimeException {
    public CanonicalConflict(String message) { super(message); }
  }
}
