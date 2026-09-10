package com.bookrush.ingestion.catalog;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Uploads a validated source representation through the catalog's administrative boundary. */
@Component
@EnableConfigurationProperties(CanonicalCatalogProperties.class)
public final class CatalogAssetClient {
  private final RestClient client;
  private final CanonicalCatalogProperties properties;

  public CatalogAssetClient(RestClient.Builder builder, CanonicalCatalogProperties properties) {
    this.properties = properties;
    this.client = builder.baseUrl(properties.baseUrl().toString()).build();
  }

  public Map<?, ?> uploadSource(UUID bookId, UUID editionId, UUID sourceId, Path file, String filename) {
    if (properties.assetToken() == null || properties.assetToken().isBlank()) return Map.of("skipped", true);
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("sourceId", sourceId.toString());
    form.add("editionId", editionId.toString());
    form.add("type", "EPUB");
    form.add("role", "SOURCE");
    form.add("file", filePart(file, MediaType.parseMediaType("application/epub+zip")));
    try {
      var existing = client.get().uri("/api/admin/books/{bookId}/assets", bookId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.assetToken()).retrieve().body(java.util.List.class);
      for (Object value : existing == null ? java.util.List.of() : existing) {
        if (!(value instanceof Map<?, ?> item) || !"SOURCE".equals(String.valueOf(item.get("role"))) || !"EPUB".equals(String.valueOf(item.get("type")))) continue;
        return client.post().uri("/api/admin/books/{bookId}/assets/{assetId}/versions", bookId, item.get("id"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.assetToken())
            .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);
      }
      return client.post().uri("/api/admin/books/{bookId}/assets", bookId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.assetToken())
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(form).retrieve().body(Map.class);
    } catch (RestClientResponseException e) {
      throw new IllegalStateException("catalog source asset upload failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
    }
  }

  public Map<?, ?> uploadDerived(UUID bookId, UUID editionId, UUID sourceId, Path file, String filename, String type) {
    if (properties.assetToken() == null || properties.assetToken().isBlank()) return Map.of("skipped", true);
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("sourceId", sourceId.toString()); form.add("editionId", editionId.toString());
    form.add("type", type); form.add("role", "PROCESSING");
    form.add("file", filePart(file, mediaType(type)));
    return client.post().uri("/api/admin/books/{bookId}/assets", bookId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.assetToken())
        .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);
  }

  private static HttpEntity<FileSystemResource> filePart(Path file, MediaType contentType) {
    var headers = new HttpHeaders();
    headers.setContentType(contentType);
    return new HttpEntity<>(new FileSystemResource(file), headers);
  }

  private static MediaType mediaType(String type) {
    return switch (type.toUpperCase(java.util.Locale.ROOT)) {
      case "TXT" -> MediaType.TEXT_PLAIN;
      case "JSON" -> MediaType.APPLICATION_JSON;
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }
}
