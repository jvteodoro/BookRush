package com.bookrush.recommendationservice.api;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private static final String MODEL_VERSION = "heuristic-v1";
  private final RestClient catalog;
  private final JdbcTemplate jdbc;

  public RecommendationController(
      @Value("${bookrush.catalog-url:http://catalog-service:8080}") String url,
      JdbcTemplate jdbc) {
    catalog = RestClient.builder().baseUrl(url).build();
    this.jdbc = jdbc;
  }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  @GetMapping("/feed")
  public Map<String, Object> feed(@RequestParam(defaultValue = "20") int size, Principal principal) {
    String subject = subject(principal);
    int limit = Math.max(1, Math.min(size, 100));
    Map<?, ?> result = catalog.get().uri("/api/v1/books?page=0&size=" + limit)
        .retrieve().body(Map.class);
    List<?> books = result != null && result.get("items") instanceof List<?> list ? list : List.of();
    UUID requestId = UUID.randomUUID();
    jdbc.update("INSERT INTO recommendation.request(id, subject_key, model_version) VALUES (?, ?, ?)",
        requestId, subject, MODEL_VERSION);
    List<Map<String, Object>> items = new ArrayList<>();
    int rank = 1;
    for (Object book : books) {
      Object bookId = book instanceof Map<?, ?> map ? map.get("id") : null;
      if (bookId == null) continue;
      UUID impressionId = UUID.randomUUID();
      jdbc.update("INSERT INTO recommendation.impression(id, request_id, book_id, rank) "
          + "VALUES (?, ?, ?, ?)", impressionId, requestId, UUID.fromString(String.valueOf(bookId)), rank);
      items.add(Map.of("book", book, "recommendationRequestId", requestId,
          "impressionId", impressionId, "modelVersion", MODEL_VERSION, "rank", rank++));
    }
    return Map.of("requestId", requestId, "modelVersion", MODEL_VERSION,
        "generatedAt", Instant.now(), "items", items);
  }

  @PostMapping("/impressions/{impressionId}/viewable")
  public Map<String, Object> viewable(@PathVariable UUID impressionId, Principal principal) {
    String subject = subject(principal);
    int updated = jdbc.update("UPDATE recommendation.impression i SET viewable = true "
        + "WHERE i.id = ? AND EXISTS (SELECT 1 FROM recommendation.request r "
        + "WHERE r.id = i.request_id AND r.subject_key = ?)", impressionId, subject);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "impression not found");
    }
    return Map.of("impressionId", impressionId, "viewable", true);
  }
}
