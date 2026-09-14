package com.bookrush.ingestion.catalog;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class ProcessingLineageClient {
  private final RestClient client;
  private final CanonicalCatalogProperties properties;
  private final ObjectMapper mapper;
  private final OidcServiceTokenProvider tokens;
  public ProcessingLineageClient(RestClient.Builder builder, CanonicalCatalogProperties properties, ObjectMapper mapper, OidcServiceTokenProvider tokens) {
    this.properties = properties; this.client = builder.baseUrl(properties.baseUrl().toString()).build();
    this.mapper = mapper; this.tokens = tokens;
  }
  public void record(UUID input, List<UUID> outputs, String type, Map<String,Object> metadata) {
    String token = tokens.token(OidcServiceTokenProvider.Kind.CANONICAL);
    var request = client.post().uri("/api/internal/v1/catalog/processings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (properties.legacyStaticEnabled()) request.header("X-Canonical-Service-Token", token);
    request
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("inputVersionId", input, "outputVersionIds", outputs, "processingType", type,
            "processor", "bookrush-text-processing", "processorVersion", "1", "metadata", metadata))
        .retrieve().toBodilessEntity();
  }

  public void projectChapters(UUID bookId, UUID editionId, UUID textVersionId, byte[] chaptersJson) throws Exception {
    String token = tokens.token(OidcServiceTokenProvider.Kind.CANONICAL);
    JsonNode document = mapper.readTree(chaptersJson);
    var chapters = new ArrayList<Map<String,Object>>();
    for (JsonNode c : document.path("chapters")) {
      var chapter = new LinkedHashMap<String, Object>();
      chapter.put("key", c.path("key").asText());
      chapter.put("parent", c.path("parent").isNull() ? null : c.path("parent").asText());
      chapter.put("position", c.path("position").asInt());
      chapter.put("title", c.path("title").asText());
      chapter.put("start", c.path("start").asInt());
      chapter.put("end", c.path("end").asInt());
      chapter.put("confidence", c.path("confidence").asText());
      chapters.add(chapter);
    }
    var request = client.post().uri("/api/internal/v1/catalog/processings/chapters").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (properties.legacyStaticEnabled()) request.header("X-Canonical-Service-Token", token);
    request
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("bookId", bookId, "editionId", editionId, "textAssetVersionId", textVersionId, "chapters", chapters))
        .retrieve().toBodilessEntity();
  }
}
