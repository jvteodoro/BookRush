package com.bookrush.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/** Paginates the public Gutenberg catalogue index; it never fetches a book asset. */
@Service
public final class GutenbergCatalogClient {
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final GutenbergProperties properties;

  public GutenbergCatalogClient(
      HttpClient http, ObjectMapper mapper, GutenbergProperties properties) {
    this.http = http;
    this.mapper = mapper;
    this.properties = properties;
  }

  public Page page(String query, int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
    var uri =
        properties.catalogUrl()
            + (properties.catalogUrl().contains("?") ? "&" : "?")
            + "page="
            + (page + 1)
            + (query == null || query.isBlank()
                ? ""
                : "&search=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
    Exception lastFailure = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        var response =
            http.send(
                HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2)
          throw new IllegalStateException(
              "Gutenberg catalogue returned HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        var results = root.path("results");
        var items = new java.util.ArrayList<Item>();
        if (results.isArray())
          for (JsonNode item : results) {
            var authors = item.path("authors");
            String author =
                authors.isArray() && authors.size() > 0
                    ? authors.get(0).path("name").asText("Unknown author")
                    : "Unknown author";
            String language =
                item.path("languages").isArray() && item.path("languages").size() > 0
                    ? item.path("languages").get(0).asText("und")
                    : "und";
            items.add(
                new Item(
                    String.valueOf(item.path("id").asInt()),
                    item.path("title").asText("Untitled"),
                    author,
                    language,
                    item.path("subjects").isArray()
                        ? mapper.convertValue(item.path("subjects"), List.class)
                        : List.of()));
          }
        return new Page(
            items,
            root.path("count").asLong(items.size()),
            page,
            size,
            root.path("next").asText(null));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Gutenberg catalogue request interrupted", e);
      } catch (IOException e) {
        lastFailure = e;
        if (attempt == 0) {
          try {
            Thread.sleep(250);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Gutenberg catalogue request interrupted", interrupted);
          }
        }
      } catch (Exception e) {
        throw new IllegalStateException("Gutenberg catalogue unavailable", e);
      }
    }
    throw new IllegalStateException("Gutenberg catalogue unavailable after retry", lastFailure);
  }

  public record Item(
      String externalId, String title, String author, String language, List<String> subjects) {}

  public record Page(List<Item> items, long totalItems, int page, int size, String next) {}
}
