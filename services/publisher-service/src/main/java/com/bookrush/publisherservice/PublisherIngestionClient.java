package com.bookrush.publisherservice;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Starts ingestion for a finalized publisher staging object. */
@Component
@EnableConfigurationProperties(PublisherIngestionClient.Properties.class)
public class PublisherIngestionClient {
  private final RestClient client;
  private final Properties properties;

  public PublisherIngestionClient(RestClient.Builder builder, Properties properties) {
    this.client = builder.baseUrl(properties.url().toString()).build();
    this.properties = properties;
  }

  public void process(UUID submissionId, String title, String objectKey, String bucket, String contentType, String sha256) {
    if (properties.token() == null || properties.token().isBlank()) {
      throw new IllegalStateException("publisher ingestion token is not configured");
    }
    client.post()
        .uri("/api/internal/v1/ingestion/publisher-submissions/process")
        .header("X-BookRush-Ingestion-Token", properties.token())
        .body(Map.of("submissionId", submissionId, "title", title, "objectKey", objectKey,
            "bucket", bucket, "contentType", contentType, "sha256", sha256))
        .retrieve()
        .toBodilessEntity();
  }

  @ConfigurationProperties(prefix = "publisher.ingestion")
  public record Properties(URI url, String token) {
    public Properties {
      url = url == null ? URI.create("http://book-ingestion-service:8090") : url;
      token = token == null ? "" : token;
    }
  }
}
