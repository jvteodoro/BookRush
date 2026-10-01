package com.bookrush.ingestion.catalog;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sends the canonical catalog identity back to a publisher submission. */
@Component
@EnableConfigurationProperties(PublisherSubmissionLinkClient.Properties.class)
public class PublisherSubmissionLinkClient {
  private final RestClient client;
  private final Properties properties;

  public PublisherSubmissionLinkClient(RestClient.Builder builder, Properties properties) {
    this.client = builder.baseUrl(properties.url().toString()).build();
    this.properties = properties;
  }

  public void link(UUID submissionId, UUID bookId, UUID jobId, String source, String externalId) {
    if (properties.token() == null || properties.token().isBlank()) {
      throw new IllegalStateException("publisher callback token is not configured");
    }
    client.post()
        .uri("/api/internal/v1/publisher/submissions/{id}/catalog-link", submissionId)
        .header("X-BookRush-Ingestion-Token", properties.token())
        .header(HttpHeaders.CONTENT_TYPE, "application/json")
        .body(Map.of(
            "catalogBookId", bookId,
            "ingestionJobId", jobId,
            "sourceCode", source,
            "sourceExternalId", externalId))
        .retrieve()
        .toBodilessEntity();
  }

  @ConfigurationProperties(prefix = "ingestion.publisher-callback")
  public record Properties(URI url, String token) {
    public Properties {
      url = url == null ? URI.create("http://publisher-service:8099") : url;
      token = token == null ? "" : token;
    }
  }
}
