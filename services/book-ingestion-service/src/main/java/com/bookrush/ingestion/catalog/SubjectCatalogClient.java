package com.bookrush.ingestion.catalog;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sends source subjects to catalog-service; the catalog owns normalization and idempotency. */
@Component
@EnableConfigurationProperties(CanonicalCatalogProperties.class)
public final class SubjectCatalogClient {
  private final RestClient client;
  private final CanonicalCatalogProperties properties;

  public SubjectCatalogClient(RestClient.Builder builder, CanonicalCatalogProperties properties) {
    this.properties = properties;
    this.client = builder.baseUrl(properties.baseUrl().toString()).build();
  }

  public Map<String, Object> assign(UUID bookId, String scheme, String term, String sourceCode,
      UUID sourceRecordId, BigDecimal confidence, String assignmentMethod) {
    var command = new SubjectCommand(bookId, scheme, term, sourceCode, sourceRecordId, confidence, assignmentMethod);
    return client.post().uri("/api/internal/v1/catalog/subjects")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceToken())
        .body(command).retrieve().body(Map.class);
  }

  public record SubjectCommand(UUID bookId, String scheme, String term, String sourceCode,
      UUID sourceRecordId, BigDecimal confidence, String assignmentMethod) {}
}
