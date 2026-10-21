package com.bookrush.recommendationservice.retrieval;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/** Catalog-backed retriever with bounded pagination and stable de-duplication. */
public final class CatalogCandidateRetriever implements CandidateRetriever {
  private final RestClient catalog;
  private final CandidateRetrievalProperties properties;
  private final MeterRegistry metrics;

  public CatalogCandidateRetriever(RestClient.Builder builder, String catalogUrl,
      CandidateRetrievalProperties properties, MeterRegistry metrics) {
    this.catalog = builder.baseUrl(catalogUrl).build();
    this.properties = properties;
    this.metrics = metrics;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CandidateSet retrieve(int requestedLimit) {
    int limit = properties.effectiveLimit(requestedLimit);
    var books = new LinkedHashMap<String, Map<String, Object>>();
    int pages = 0;
    try {
      for (int page = 0; page < properties.maxPages() && books.size() < limit; page++) {
        Map<?, ?> response = catalog.get().uri(uri(page)).retrieve().body(Map.class);
        pages++;
        List<?> items = response != null && response.get("items") instanceof List<?> list
            ? list : List.of();
        for (Object item : items) {
          if (!(item instanceof Map<?, ?> raw) || raw.get("id") == null) continue;
          String id = String.valueOf(raw.get("id"));
          if (!books.containsKey(id)) {
            var copy = new LinkedHashMap<String, Object>();
            raw.forEach((key, value) -> copy.put(String.valueOf(key), value));
            books.put(id, copy);
          }
          if (books.size() >= limit) break;
        }
        if (items.size() < properties.pageSize()) break;
      }
      metrics.counter("bookrush.feed.candidates", "outcome", "success").increment();
      metrics.counter("bookrush.feed.candidates", "outcome", "returned").increment(books.size());
      metrics.counter("bookrush.feed.candidates.pages").increment(pages);
      return new CandidateSet(properties.version(), configurationHash(), "catalog",
          new ArrayList<>(books.values()));
    } catch (RuntimeException error) {
      metrics.counter("bookrush.feed.candidates", "outcome", "error").increment();
      throw error;
    }
  }

  private String uri(int page) {
    return "/api/v1/books?page=" + page + "&size=" + properties.pageSize();
  }

  private String configurationHash() {
    String input = properties.version() + ":" + properties.maxCandidates() + ":"
        + properties.pageSize() + ":" + properties.maxPages();
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required for candidate provenance", exception);
    }
  }
}
