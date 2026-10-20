package com.bookrush.recommendationservice.api;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import com.bookrush.recommendationservice.BehaviorEventPublisher;
import com.bookrush.recommendationservice.RecommendationHeuristic;
import com.bookrush.recommendationservice.RecommendationHeuristic.Strategy;
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
import com.bookrush.recommendationservice.excerpt.ExcerptEnrichmentService;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private static final String MODEL_VERSION = "heuristic-v1";
  private final RestClient catalog;
  private final JdbcTemplate jdbc;
  private final ExcerptEnrichmentService excerptEnrichment;
  private final BehaviorEventPublisher behavior;

  public RecommendationController(
      @Value("${bookrush.catalog-url:http://catalog-service:8080}") String url,
      JdbcTemplate jdbc,
      ExcerptEnrichmentService excerptEnrichment,
      BehaviorEventPublisher behavior) {
    catalog = RestClient.builder().baseUrl(url).build();
    this.jdbc = jdbc;
    this.excerptEnrichment = excerptEnrichment;
    this.behavior = behavior;
  }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  @GetMapping("/feed")
  public Map<String, Object> feed(@RequestParam(defaultValue = "20") int size, Principal principal,
      HttpServletRequest request) {
    String subject = subject(principal);
    int limit = Math.max(1, Math.min(size, 100));
    long previousImpressions = jdbc.queryForObject(
        "SELECT count(*) FROM recommendation.impression i JOIN recommendation.request r ON r.id=i.request_id WHERE r.subject_key=?",
        Long.class, subject);
    Strategy selectedStrategy = RecommendationHeuristic.strategyFor(previousImpressions);
    String strategy = selectedStrategy.name();
    Map<?, ?> result = catalog.get().uri("/api/v1/books?page=0&size=" + Math.min(100, Math.max(limit * 3, 20)))
        .retrieve().body(Map.class);
    List<?> books = result != null && result.get("items") instanceof List<?> list ? list : List.of();
    List<?> rankedBooks = RecommendationHeuristic.rank(books, subject, limit, selectedStrategy);
    UUID requestId = UUID.randomUUID();
    jdbc.update("INSERT INTO recommendation.request(id, subject_key, model_version, strategy, random_seed, context) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
        requestId, subject, MODEL_VERSION, strategy, subject.hashCode(), "{}");
    List<Map<String, Object>> items = new ArrayList<>();
    int rank = 1;
    for (Object book : rankedBooks) {
      Object bookId = book instanceof Map<?, ?> map ? map.get("id") : null;
      if (bookId == null) continue;
      UUID parsedBookId = UUID.fromString(String.valueOf(bookId));
      UUID itemId = UUID.randomUUID();
      int currentRank = rank;
      String reason = selectedStrategy == Strategy.COLD_START ? "popular-cold-start"
          : selectedStrategy == Strategy.LUCKY ? "lucky-exploration" : "diversity-heuristic";
      jdbc.update("INSERT INTO recommendation.item(id, request_id, book_id, rank, score, reason) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
          itemId, requestId, parsedBookId, currentRank, 1.0 / currentRank,
          "{\"strategy\":\"" + reason + "\"}");
      UUID impressionId = UUID.randomUUID();
      jdbc.update("INSERT INTO recommendation.impression(id, request_id, book_id, rank) "
          + "VALUES (?, ?, ?, ?)", impressionId, requestId, parsedBookId, currentRank);
      jdbc.update("UPDATE recommendation.impression SET item_id=? WHERE id=?", itemId, impressionId);
      var item = new java.util.LinkedHashMap<String, Object>();
      item.put("book", book);
      item.put("recommendationRequestId", requestId);
      item.put("impressionId", impressionId);
      item.put("modelVersion", MODEL_VERSION);
      item.put("rank", rank++);
      item.put("reason", reason);
      items.add(item);
    }
    behavior.publish("RECOMMENDATION_REQUEST", null, Map.of("size", limit, "modelVersion", MODEL_VERSION),
        request.getHeader("Authorization"));
    excerptEnrichment.enrich(items);
    return Map.of("requestId", requestId, "modelVersion", MODEL_VERSION, "strategy", strategy,
        "generatedAt", Instant.now(), "items", items);
  }

  @PostMapping("/impressions/{impressionId}/viewable")
  public Map<String, Object> viewable(@PathVariable UUID impressionId, Principal principal,
      HttpServletRequest request) {
    String subject = subject(principal);
    int updated = jdbc.update("UPDATE recommendation.impression i SET viewable = true "
        + "WHERE i.id = ? AND EXISTS (SELECT 1 FROM recommendation.request r "
        + "WHERE r.id = i.request_id AND r.subject_key = ?)", impressionId, subject);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "impression not found");
    }
    behavior.publish("EXCERPT_IMPRESSION", null, Map.of("impressionId", impressionId),
        request.getHeader("Authorization"));
    return Map.of("impressionId", impressionId, "viewable", true);
  }
}
