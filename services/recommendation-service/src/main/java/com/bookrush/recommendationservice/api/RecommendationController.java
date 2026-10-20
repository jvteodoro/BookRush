package com.bookrush.recommendationservice.api;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookrush.recommendationservice.BehaviorEventPublisher;
import com.bookrush.recommendationservice.RecommendationHeuristic;
import com.bookrush.recommendationservice.RecommendationHeuristic.Strategy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import com.bookrush.recommendationservice.excerpt.ExcerptEnrichmentService;
import com.bookrush.recommendationservice.ranking.AnalyticsRankingProperties;
import com.bookrush.recommendationservice.ranking.AnalyticsScoreClient;
import com.bookrush.recommendationservice.ranking.PersonalizedFeedGate;
import com.bookrush.recommendationservice.observability.FeedMetrics;
import com.bookrush.recommendationservice.fatigue.ExposureFatigue;
import com.bookrush.recommendationservice.retrieval.CandidateRetriever;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private static final String MODEL_VERSION = "heuristic-v1";
  private final CandidateRetriever candidateRetriever;
  private final JdbcTemplate jdbc;
  private final ExcerptEnrichmentService excerptEnrichment;
  private final BehaviorEventPublisher behavior;
  private final AnalyticsScoreClient analyticsScores;
  private final AnalyticsRankingProperties rankingProperties;
  private final FeedMetrics feedMetrics;
  private final ObjectMapper mapper;
  private final ExposureFatigue fatiguePolicy;
  private final PersonalizedFeedGate personalizedFeedGate;

  public RecommendationController(
      CandidateRetriever candidateRetriever,
      JdbcTemplate jdbc,
      ExcerptEnrichmentService excerptEnrichment,
      BehaviorEventPublisher behavior,
      AnalyticsScoreClient analyticsScores,
      AnalyticsRankingProperties rankingProperties,
      FeedMetrics feedMetrics,
      ObjectMapper mapper,
      ExposureFatigue fatiguePolicy,
      PersonalizedFeedGate personalizedFeedGate) {
    this.candidateRetriever = candidateRetriever;
    this.jdbc = jdbc;
    this.excerptEnrichment = excerptEnrichment;
    this.behavior = behavior;
    this.analyticsScores = analyticsScores;
    this.rankingProperties = rankingProperties;
    this.feedMetrics = feedMetrics;
    this.mapper = mapper;
    this.fatiguePolicy = fatiguePolicy;
    this.personalizedFeedGate = personalizedFeedGate;
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
    var candidateSet = feedMetrics.time("catalog_fetch", () ->
        candidateRetriever.retrieve(Math.max(limit * 3, 20)));
    List<?> books = candidateSet.books();
    var candidateIds = books.stream().map(this::bookId).filter(java.util.Objects::nonNull).toList();
    var scoreById = feedMetrics.time("analytics_scores", () -> analyticsScores.find(candidateIds).entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue)));
    var personalizationDecision = personalizedFeedGate.decide(null, false, !scoreById.isEmpty());
    feedMetrics.count("bookrush.feed.personalized.path",
        personalizationDecision.path().name().toLowerCase(java.util.Locale.ROOT), 1);
    var fatigueById = loadFatigue(subject, candidateIds);
    boolean analyticsRequired = rankingProperties.enabled() && rankingProperties.configured();
    List<?> rankedBooks = feedMetrics.time("ranking",
        () -> RecommendationHeuristic.rank(books, subject, limit, selectedStrategy, scoreById,
            fatigueById, Instant.now(), fatiguePolicy));
    if (analyticsRequired) {
      rankedBooks = rankedBooks.stream()
          .filter(book -> scoreById.containsKey(String.valueOf(bookId(book))))
          .toList();
    }
    boolean analyticsRanked = analyticsRequired && !scoreById.isEmpty();
    String modelVersion = analyticsRequired ? rankingProperties.modelVersion() : MODEL_VERSION;
    String candidateSetHash = RecommendationProvenance.candidateSetHash(candidateSet, scoreById, modelVersion);
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
      var bookScore = scoreById.get(String.valueOf(bookId));
      if (bookScore != null) item.put("score", bookScore.score());
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
          requestId, subject, modelVersion, strategy, subject.hashCode(),
          mapper.createObjectNode()
              .put("candidateSetHash", candidateSetHash)
              .put("personalizationPath", personalizationDecision.path().name())
              .put("personalizationReason", personalizationDecision.reason())
              .toString());
      jdbc.update("UPDATE recommendation.request SET candidate_set_hash=? WHERE id=?",
          candidateSetHash, requestId);
      int rank = 1;
      for (Map<String, Object> item : items) {
        UUID parsedBookId = UUID.fromString(String.valueOf(((Map<?, ?>) item.get("book")).get("id")));
        UUID itemId = UUID.randomUUID();
        UUID impressionId = UUID.randomUUID();
        item.put("recommendationItemId", itemId);
        item.put("recommendationRequestId", requestId);
        item.put("impressionId", impressionId);
        item.put("rank", rank);
        Object excerpt = item.get("excerpt");
        UUID excerptId = excerpt instanceof Map<?, ?> excerptMap && excerptMap.get("id") != null
            ? UUID.fromString(String.valueOf(excerptMap.get("id"))) : null;
        var reasonPayload = new java.util.LinkedHashMap<String, Object>();
        reasonPayload.put("strategy", strategy);
        reasonPayload.put("reason", item.get("reason"));
        reasonPayload.put("rankerVersion", modelVersion);
        if (item.get("score") != null) reasonPayload.put("score", item.get("score"));
        reasonPayload.put("candidateSetHash", candidateSetHash);
        var fatigue = fatigueById.get(String.valueOf(parsedBookId));
        if (fatigue != null) {
          reasonPayload.put("fatigueViewableCount", fatigue.viewableCount());
          reasonPayload.put("fatigueLastViewableAt", fatigue.lastViewableAt());
          reasonPayload.put("fatiguePenalty", fatiguePolicy.penalty(fatigue, Instant.now()));
        }
        jdbc.update("INSERT INTO recommendation.item(id, request_id, book_id, excerpt_id, rank, score, reason) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
            itemId, requestId, parsedBookId, excerptId, rank, item.get("score"),
            json(reasonPayload));
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
        "candidateSetHash", candidateSetHash, "generatedAt", Instant.now(), "items", items);
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

  private Map<String, ExposureFatigue.Signal> loadFatigue(String subject, List<UUID> bookIds) {
    if (bookIds.isEmpty()) return Map.of();
    String placeholders = String.join(",", java.util.Collections.nCopies(bookIds.size(), "?"));
    var args = new Object[bookIds.size() + 1];
    args[0] = subject;
    for (int i = 0; i < bookIds.size(); i++) args[i + 1] = bookIds.get(i);
    String sql = """
        SELECT i.book_id, count(*) FILTER (WHERE i.viewable) AS viewable_count,
               max(i.shown_at) FILTER (WHERE i.viewable) AS last_viewable_at
        FROM recommendation.impression i
        JOIN recommendation.request r ON r.id = i.request_id
        WHERE r.subject_key = ? AND i.book_id IN (""" + placeholders + """
        )
        GROUP BY i.book_id
        """;
    return jdbc.query(sql, args, (rs, rowNum) -> Map.entry(
            rs.getString("book_id"), new ExposureFatigue.Signal(rs.getLong("viewable_count"),
                rs.getTimestamp("last_viewable_at") == null ? null
                    : rs.getTimestamp("last_viewable_at").toInstant())))
        .stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private UUID bookId(Object book) {
    if (!(book instanceof Map<?, ?> map) || map.get("id") == null) return null;
    try {
      return UUID.fromString(String.valueOf(map.get("id")));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("cannot serialize recommendation provenance", exception);
    }
  }

  @PostMapping("/impressions/{impressionId}/viewable")
  public Map<String, Object> viewable(@PathVariable UUID impressionId, Principal principal,
      HttpServletRequest request) {
    String subject = subject(principal);
    List<Map<String, Object>> owned = jdbc.queryForList("""
        SELECT i.item_id, item.book_id
        FROM recommendation.impression i
        JOIN recommendation.request r ON r.id = i.request_id
        LEFT JOIN recommendation.item item ON item.id = i.item_id
        WHERE i.id = ? AND r.subject_key = ?
        """, impressionId, subject);
    if (owned.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "impression not found");
    }
    Map<String, Object> row = owned.get(0);
    UUID itemId = row.get("item_id") == null ? null : UUID.fromString(String.valueOf(row.get("item_id")));
    UUID bookId = row.get("book_id") == null ? null : UUID.fromString(String.valueOf(row.get("book_id")));
    jdbc.update("UPDATE recommendation.impression SET viewable = true WHERE id = ?", impressionId);
    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("impressionId", impressionId);
    if (itemId != null) payload.put("recommendationItemId", itemId);
    behavior.publish("EXCERPT_IMPRESSION", bookId, payload,
        request.getHeader("Authorization"));
    return Map.of("impressionId", impressionId, "viewable", true);
  }
}
