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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import com.bookrush.recommendationservice.excerpt.ExcerptEnrichmentService;
import com.bookrush.recommendationservice.ranking.AnalyticsRankingProperties;
import com.bookrush.recommendationservice.ranking.AnalyticsScoreClient;
import com.bookrush.recommendationservice.observability.FeedMetrics;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private static final String MODEL_VERSION = "heuristic-v1";
  private final RestClient catalog;
  private final JdbcTemplate jdbc;
  private final ExcerptEnrichmentService excerptEnrichment;
  private final BehaviorEventPublisher behavior;
  private final AnalyticsScoreClient analyticsScores;
  private final AnalyticsRankingProperties rankingProperties;
  private final FeedMetrics feedMetrics;

  public RecommendationController(
      @Value("${bookrush.catalog-url:http://catalog-service:8080}") String url,
      JdbcTemplate jdbc,
      ExcerptEnrichmentService excerptEnrichment,
      BehaviorEventPublisher behavior,
      AnalyticsScoreClient analyticsScores,
      AnalyticsRankingProperties rankingProperties,
      FeedMetrics feedMetrics) {
    catalog = RestClient.builder().baseUrl(url).build();
    this.jdbc = jdbc;
    this.excerptEnrichment = excerptEnrichment;
    this.behavior = behavior;
    this.analyticsScores = analyticsScores;
    this.rankingProperties = rankingProperties;
    this.feedMetrics = feedMetrics;
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
    var totalSample = feedMetrics.start();
    boolean resultRecorded = false;
    try {
      String subject = subject(principal);
    int limit = Math.max(1, Math.min(size, 100));
    long previousImpressions = feedMetrics.time("history_count", () -> jdbc.queryForObject(
        "SELECT count(*) FROM recommendation.impression i JOIN recommendation.request r ON r.id=i.request_id WHERE r.subject_key=?",
        Long.class, subject));
    Strategy selectedStrategy = RecommendationHeuristic.strategyFor(previousImpressions);
    String strategy = selectedStrategy.name();
    Map<?, ?> result = feedMetrics.time("catalog_fetch", () -> catalog.get()
        .uri("/api/v1/books?page=0&size=" + Math.min(100, Math.max(limit * 3, 20)))
        .retrieve().body(Map.class));
    List<?> books = result != null && result.get("items") instanceof List<?> list ? list : List.of();
    var candidateIds = books.stream().map(this::bookId).filter(java.util.Objects::nonNull).toList();
    var scoreById = feedMetrics.time("analytics_scores", () -> analyticsScores.find(candidateIds).entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue)));
    boolean analyticsRequired = rankingProperties.enabled() && rankingProperties.configured();
    List<?> rankedBooks = feedMetrics.time("ranking",
        () -> RecommendationHeuristic.rank(books, subject, limit, selectedStrategy, scoreById));
    if (analyticsRequired) {
      rankedBooks = rankedBooks.stream()
          .filter(book -> scoreById.containsKey(String.valueOf(bookId(book))))
          .toList();
    }
    boolean analyticsRanked = analyticsRequired && !scoreById.isEmpty();
    String modelVersion = analyticsRequired ? rankingProperties.modelVersion() : MODEL_VERSION;
    UUID requestId = UUID.randomUUID();
    List<Map<String, Object>> candidates = new ArrayList<>();
    int candidateRank = 1;
    for (Object book : rankedBooks) {
      Object bookId = book instanceof Map<?, ?> map ? map.get("id") : null;
      if (bookId == null) continue;
      String reason = analyticsRanked && scoreById.containsKey(String.valueOf(bookId)) ? "analytics-score"
          : selectedStrategy == Strategy.COLD_START ? "popular-cold-start"
          : selectedStrategy == Strategy.LUCKY ? "lucky-exploration" : "diversity-heuristic";
      var item = new java.util.LinkedHashMap<String, Object>();
      item.put("book", book);
      item.put("modelVersion", modelVersion);
      item.put("rank", candidateRank++);
      item.put("reason", reason);
      candidates.add(item);
    }
    feedMetrics.time("behavior_publish", () -> {
      behavior.publish("RECOMMENDATION_REQUEST", null, Map.of("size", limit, "modelVersion", modelVersion),
          request.getHeader("Authorization"));
      return null;
    });
    feedMetrics.time("excerpt_enrichment", () -> {
      excerptEnrichment.enrich(candidates);
      return null;
    });
    List<Map<String, Object>> items = analyticsRequired
        ? new ArrayList<>(FeedEligibility.retainAnalyticsReady(candidates))
        : candidates;
    feedMetrics.time("ledger_persist", () -> {
      jdbc.update("INSERT INTO recommendation.request(id, subject_key, model_version, strategy, random_seed, context) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
          requestId, subject, modelVersion, strategy, subject.hashCode(), "{}");
      int rank = 1;
      for (Map<String, Object> item : items) {
        UUID parsedBookId = UUID.fromString(String.valueOf(((Map<?, ?>) item.get("book")).get("id")));
        UUID itemId = UUID.randomUUID();
        UUID impressionId = UUID.randomUUID();
        item.put("recommendationRequestId", requestId);
        item.put("impressionId", impressionId);
        item.put("rank", rank);
        jdbc.update("INSERT INTO recommendation.item(id, request_id, book_id, rank, score, reason) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
            itemId, requestId, parsedBookId, rank, 1.0 / rank,
            "{\"strategy\":\"" + item.get("reason") + "\",\"rankerVersion\":\""
                + modelVersion + "\"}");
        jdbc.update("INSERT INTO recommendation.impression(id, request_id, book_id, rank, item_id) "
            + "VALUES (?, ?, ?, ?, ?)", impressionId, requestId, parsedBookId, rank, itemId);
        rank++;
      }
      return null;
    });
    feedMetrics.observeFeed(candidateIds.size(), scoreById.size(), rankedBooks.size(), items.size(),
        (int) items.stream().filter(FeedEligibility::hasAnalyticsExcerpt).count());
    feedMetrics.recordResult(items.isEmpty() ? "empty" : "success");
    resultRecorded = true;
    return Map.of("requestId", requestId, "modelVersion", modelVersion,
        "rankingVersion", modelVersion, "strategy", strategy,
        "generatedAt", Instant.now(), "items", items);
    } catch (ResourceAccessException timeout) {
      feedMetrics.recordResult("timeout");
      resultRecorded = true;
      throw timeout;
    } catch (RuntimeException error) {
      feedMetrics.recordResult("error");
      resultRecorded = true;
      throw error;
    } finally {
      if (!resultRecorded) feedMetrics.recordResult("error");
      feedMetrics.stopTotal(totalSample);
    }
  }

  private UUID bookId(Object book) {
    if (!(book instanceof Map<?, ?> map) || map.get("id") == null) return null;
    try {
      return UUID.fromString(String.valueOf(map.get("id")));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
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
