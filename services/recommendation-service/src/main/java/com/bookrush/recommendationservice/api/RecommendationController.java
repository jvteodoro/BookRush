package com.bookrush.recommendationservice.api;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.bookrush.recommendationservice.ranking.PersonalizedDecisionReplay;
import com.bookrush.recommendationservice.ranking.PersonalizedProfileService;
import com.bookrush.recommendationservice.ranking.PersonalizedRankerV1;
import com.bookrush.recommendationservice.ranking.BookDiversityMmr;
import com.bookrush.recommendationservice.ranking.ExplorationPolicy;
import com.bookrush.recommendationservice.ranking.PersonalizedFeedSelector;
import com.bookrush.recommendationservice.ranking.BehaviorSignalsClient;
import com.bookrush.recommendationservice.observability.FeedMetrics;
import com.bookrush.recommendationservice.fatigue.ExposureFatigue;
import com.bookrush.recommendationservice.retrieval.CandidateRetriever;
import com.bookrush.recommendationservice.retrieval.CandidateUnionBuilder;
import com.bookrush.recommendationservice.retrieval.CandidateSet;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private static final String MODEL_VERSION = "heuristic-v1";
  private final CandidateRetriever candidateRetriever;
  private final CandidateUnionBuilder candidateUnionBuilder;
  private final JdbcTemplate jdbc;
  private final ExcerptEnrichmentService excerptEnrichment;
  private final BehaviorEventPublisher behavior;
  private final AnalyticsScoreClient analyticsScores;
  private final AnalyticsRankingProperties rankingProperties;
  private final FeedMetrics feedMetrics;
  private final ObjectMapper mapper;
  private final ExposureFatigue fatiguePolicy;
  private final PersonalizedFeedGate personalizedFeedGate;
  private final PersonalizedProfileService personalizedProfiles;
  private final PersonalizedRankerV1 personalizedRanker;
  private final BookDiversityMmr bookDiversityMmr;
  private final ExplorationPolicy explorationPolicy;
  private final PersonalizedFeedSelector personalizedFeedSelector;
  private final PersonalizedDecisionReplay decisionReplay;

  public RecommendationController(
      CandidateRetriever candidateRetriever,
      CandidateUnionBuilder candidateUnionBuilder,
      JdbcTemplate jdbc,
      ExcerptEnrichmentService excerptEnrichment,
      BehaviorEventPublisher behavior,
      AnalyticsScoreClient analyticsScores,
      AnalyticsRankingProperties rankingProperties,
      FeedMetrics feedMetrics,
      ObjectMapper mapper,
      ExposureFatigue fatiguePolicy,
      PersonalizedFeedGate personalizedFeedGate,
      PersonalizedProfileService personalizedProfiles,
      PersonalizedRankerV1 personalizedRanker,
      BookDiversityMmr bookDiversityMmr,
      ExplorationPolicy explorationPolicy,
      PersonalizedFeedSelector personalizedFeedSelector,
      PersonalizedDecisionReplay decisionReplay) {
    this.candidateRetriever = candidateRetriever;
    this.candidateUnionBuilder = candidateUnionBuilder;
    this.jdbc = jdbc;
    this.excerptEnrichment = excerptEnrichment;
    this.behavior = behavior;
    this.analyticsScores = analyticsScores;
    this.rankingProperties = rankingProperties;
    this.feedMetrics = feedMetrics;
    this.mapper = mapper;
    this.fatiguePolicy = fatiguePolicy;
    this.personalizedFeedGate = personalizedFeedGate;
    this.personalizedProfiles = personalizedProfiles;
    this.personalizedRanker = personalizedRanker;
    this.bookDiversityMmr = bookDiversityMmr;
    this.explorationPolicy = explorationPolicy;
    this.personalizedFeedSelector = personalizedFeedSelector;
    this.decisionReplay = decisionReplay;
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
    var retrievedCandidateSet = feedMetrics.time("catalog_fetch", () ->
        candidateRetriever.retrieve(candidateRetriever.configuredLimit()));
    List<?> retrievedBooks = retrievedCandidateSet.books();
    var retrievedCandidateIds = retrievedBooks.stream().map(this::bookId).filter(java.util.Objects::nonNull).toList();
    var scoreById = feedMetrics.time("analytics_scores", () -> analyticsScores.find(retrievedCandidateIds).entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue)));
    var profileResult = personalizedFeedGate.mode() == PersonalizedFeedGate.Mode.OFF
        ? new PersonalizedProfileService.Result(null, false, false, List.of(), Map.of())
        : feedMetrics.time("personalized_profile", () -> personalizedProfiles.load(subject));
    var personalizationDecision = personalizedFeedGate.decide(profileResult.profile(),
        profileResult.behaviorAvailable(), profileResult.snapshotAvailable(), subject);
    boolean personalized = personalizationDecision.path() == PersonalizedFeedGate.Path.PERSONALIZED;
    var candidateSnapshots = personalizationDecision.path() == PersonalizedFeedGate.Path.FALLBACK
        ? Map.<UUID, AnalyticsScoreClient.BookFeatureSnapshot>of()
        : feedMetrics.time("personalized_candidate_embeddings", () ->
            analyticsScores.findSnapshots(retrievedCandidateIds, true));
    var trending = personalizationDecision.path() == PersonalizedFeedGate.Path.FALLBACK
        ? List.<BehaviorSignalsClient.Trending>of()
        : feedMetrics.time("personalized_trending", personalizedProfiles::loadTrending);
    final CandidateSet candidateSet = personalizationDecision.path() == PersonalizedFeedGate.Path.FALLBACK
        ? retrievedCandidateSet
        : buildLiveCandidateUnion(profileResult.profile(), retrievedBooks, scoreById,
            candidateSnapshots, trending);
    final List<?> books = candidateSet.books();
    final List<UUID> candidateIds = books.stream().map(this::bookId)
        .filter(java.util.Objects::nonNull).toList();
    UUID requestId = UUID.randomUUID();
    var fatigueById = loadFatigue(subject, candidateIds);
    boolean analyticsRequired = rankingProperties.enabled() && rankingProperties.configured();
    List<?> rankedBooks;
    PersonalizedRankingResult personalizedRanking = new PersonalizedRankingResult(List.of(), Map.of(), Map.of(), Set.of());
    if (personalized) {
      personalizedRanking = feedMetrics.time("ranking", () -> personalizedRank(books, scoreById, fatigueById,
          profileResult, candidateSnapshots, trending, requestId.getLeastSignificantBits(), limit));
      rankedBooks = personalizedRanking.books();
    } else {
      rankedBooks = feedMetrics.time("ranking",
          () -> RecommendationHeuristic.rank(books, subject, limit, selectedStrategy, scoreById,
              fatigueById, Instant.now(), fatiguePolicy));
    }
    var explorationIds = personalizedRanking.explorationIds();
    if (analyticsRequired && !personalized) {
      rankedBooks = rankedBooks.stream()
          .filter(book -> scoreById.containsKey(String.valueOf(bookId(book))))
          .toList();
    }
    boolean analyticsRanked = analyticsRequired && !scoreById.isEmpty();
    String modelVersion = personalized ? PersonalizedRankerV1.MODEL_VERSION
        : analyticsRequired ? rankingProperties.modelVersion() : MODEL_VERSION;
    String candidateSetHash = RecommendationProvenance.candidateSetHash(candidateSet, scoreById, modelVersion);
    if (personalizationDecision.path() == PersonalizedFeedGate.Path.SHADOW) {
      final var shadowInput = rankedBooks;
      var shadowRanking = feedMetrics.time("personalized_shadow", () -> personalizedRank(books,
          scoreById, fatigueById, profileResult, candidateSnapshots, trending,
          requestId.getLeastSignificantBits(), limit));
      var baselineIds = rankedBooks.stream().map(this::bookId).filter(java.util.Objects::nonNull)
          .map(UUID::toString).limit(limit).toList();
      var shadowIds = shadowRanking.books().stream().map(this::bookId).filter(java.util.Objects::nonNull)
          .map(UUID::toString).limit(limit).toList();
      int overlap = (int) shadowIds.stream().filter(baselineIds::contains).count();
      int rankMovement = 0;
      for (int index = 0; index < shadowIds.size(); index++) {
        int baselineIndex = baselineIds.indexOf(shadowIds.get(index));
        if (baselineIndex >= 0) rankMovement += Math.abs(index - baselineIndex);
      }
      decisionReplay.replayIds(shadowIds,
          profileResult.profile() == null ? "" : profileResult.profile().profileVersion(),
          RecommendationProvenance.candidateSetHash(candidateSet, scoreById,
              PersonalizedRankerV1.MODEL_VERSION), personalizedRanker.configurationHash());
      feedMetrics.observePersonalization(personalizationDecision.path().name(),
          personalizationDecision.reason(), profileResult.profile() == null ? 0 : profileResult.profile().confidence(),
          profileResult.signals().size(), overlap, 1, rankMovement);
    } else {
      feedMetrics.observePersonalization(personalizationDecision.path().name(),
          personalizationDecision.reason(), profileResult.profile() == null ? 0 : profileResult.profile().confidence(),
          profileResult.signals().size(), 0, 0);
    }
    List<Map<String, Object>> candidates = new ArrayList<>();
    int candidateRank = 1;
    for (Object book : rankedBooks) {
      Object bookId = book instanceof Map<?, ?> map ? map.get("id") : null;
      if (bookId == null) continue;
      String reason = personalized && explorationIds.contains(String.valueOf(bookId)) ? "personalized-exploration"
          : personalized ? "personalized-score"
          : analyticsRanked && scoreById.containsKey(String.valueOf(bookId)) ? "analytics-score"
          : selectedStrategy == Strategy.COLD_START ? "popular-cold-start"
          : selectedStrategy == Strategy.LUCKY ? "lucky-exploration" : "diversity-heuristic";
      var item = new java.util.LinkedHashMap<String, Object>();
      item.put("book", publicBook(book));
      item.put("modelVersion", modelVersion);
      item.put("rank", candidateRank++);
      item.put("reason", reason);
      item.put("candidateSources", candidateSet.sourcesFor(String.valueOf(bookId)));
      var bookScore = scoreById.get(String.valueOf(bookId));
      var personalizedScore = personalizedRanking.scoredById().get(String.valueOf(bookId));
      if (personalizedScore != null) item.put("score", personalizedScore.score());
      else if (bookScore != null) item.put("score", bookScore.score());
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
    List<Map<String, Object>> items = analyticsRequired && !personalized
        ? new ArrayList<>(FeedEligibility.retainAnalyticsReady(candidates))
        : candidates;
    final var ledgerPersonalizedRanking = personalizedRanking;
    feedMetrics.time("ledger_persist", () -> {
      jdbc.update("INSERT INTO recommendation.request(id, subject_key, model_version, strategy, random_seed, context) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
          requestId, subject, modelVersion, strategy, requestId.getLeastSignificantBits(),
          mapper.createObjectNode()
              .put("candidateSetHash", candidateSetHash)
              .put("personalizationPath", personalizationDecision.path().name())
              .put("personalizationReason", personalizationDecision.reason())
              .put("personalizationModel", personalized ? PersonalizedRankerV1.MODEL_VERSION : modelVersion)
              .put("personalizationRankerConfigHash",
                  personalized ? personalizedRanker.configurationHash() : "")
              .put("personalizationProfileConfigHash", personalized && profileResult.profile() != null
                  ? profileResult.profile().configurationHash() : "")
              .put("personalizationMmrConfigHash",
                  personalized ? bookDiversityMmr.configurationHash() : "")
              .put("personalizationExplorationPolicy",
                  personalized ? explorationPolicy.policyVersion() : "")
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
        reasonPayload.put("candidateSources", candidateSet.sourcesFor(parsedBookId.toString()));
        reasonPayload.put("candidateSetVersion", candidateSet.version());
        reasonPayload.put("candidateConfigurationHash", candidateSet.configurationHash());
        if (personalized) {
          var scored = ledgerPersonalizedRanking.scoredById().get(parsedBookId.toString());
          if (scored != null) reasonPayload.put("personalizedComponents", scored.breakdown());
          var selection = ledgerPersonalizedRanking.selectionsById().get(parsedBookId.toString());
          if (selection != null) {
            reasonPayload.put("mmrScore", selection.mmrScore());
            reasonPayload.put("mmrMaxSimilarity", selection.maxSimilarity());
            reasonPayload.put("mmrRationale", selection.rationale());
          }
          reasonPayload.put("rankerConfigHash", personalizedRanker.configurationHash());
          reasonPayload.put("profileConfigHash",
              profileResult.profile() == null ? "" : profileResult.profile().configurationHash());
          reasonPayload.put("mmrConfigHash", bookDiversityMmr.configurationHash());
          reasonPayload.put("explorationPolicy", explorationPolicy.policyVersion());
          var embeddingSnapshot = candidateSnapshots.get(parsedBookId);
          if (embeddingSnapshot != null && embeddingSnapshot.embedding() != null) {
            reasonPayload.put("embeddingModel", embeddingSnapshot.embedding().model());
            reasonPayload.put("embeddingModelVersion", embeddingSnapshot.embedding().modelVersion());
            reasonPayload.put("embeddingSourceAssetVersionId",
                embeddingSnapshot.embedding().sourceAssetVersionId());
          }
        }
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
    var personalizationMetadata = Map.of(
        "path", personalizationDecision.path().name(),
        "reason", personalizationDecision.reason(),
        "profileVersion", profileResult.profile() == null ? "" : profileResult.profile().profileVersion(),
        "profileCoverage", profileResult.profile() == null ? 0 : profileResult.profile().coverage(),
        "profileConfidence", profileResult.profile() == null ? 0 : profileResult.profile().confidence(),
        "rankerConfigHash", personalized ? personalizedRanker.configurationHash() : "",
        "mmrConfigHash", personalized ? bookDiversityMmr.configurationHash() : "",
        "explorationPolicy", personalized ? explorationPolicy.policyVersion() : "");
    return Map.of("requestId", requestId, "modelVersion", modelVersion,
        "rankingVersion", modelVersion, "strategy", strategy,
        "candidateSetHash", candidateSetHash, "personalization", personalizationMetadata,
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

  private PersonalizedRankingResult personalizedRank(List<?> books,
      Map<String, AnalyticsScoreClient.BookScore> scoreById,
      Map<String, ExposureFatigue.Signal> fatigueById, PersonalizedProfileService.Result profile,
      Map<UUID, AnalyticsScoreClient.BookFeatureSnapshot> candidateSnapshots,
      List<BehaviorSignalsClient.Trending> trending,
      long seed, int limit) {
    var originals = new java.util.LinkedHashMap<String, Object>();
    var candidates = new ArrayList<PersonalizedRankerV1.Candidate>();
    var embeddings = new java.util.HashMap<String, List<Double>>();
    var trendById = trending.stream().collect(java.util.stream.Collectors.toMap(
        BehaviorSignalsClient.Trending::bookId, BehaviorSignalsClient.Trending::score,
        Math::max));
    Instant now = Instant.now();
    for (Object book : books) {
      UUID id = bookId(book);
      if (id == null) continue;
      String key = id.toString();
      var candidateSnapshot = candidateSnapshots.get(id);
      double affinity = candidateSnapshot == null || candidateSnapshot.embedding() == null
          ? 0 : cosine(profile.profile() == null ? null : profile.profile().vector(),
              candidateSnapshot.embedding().vector());
      var score = scoreById.get(key);
      Double quality = score == null ? null : Math.max(0, Math.min(1, score.score()));
      double freshness = fatiguePolicy.freshness(fatigueById.get(key), now);
      double trend = trendById.getOrDefault(id, 0d);
      candidates.add(new PersonalizedRankerV1.Candidate(key,
          new PersonalizedRankerV1.Components(affinity, quality, trend,
              null, freshness)));
      originals.putIfAbsent(key, book);
      var embeddingSnapshot = candidateSnapshots.get(id);
      if (embeddingSnapshot != null && embeddingSnapshot.embedding() != null
          && embeddingSnapshot.embedding().vector() != null) {
        embeddings.put(key, embeddingSnapshot.embedding().vector());
      }
    }
    var selected = personalizedFeedSelector.select(candidates, embeddings, limit, seed);
    var selectedBooks = selected.selections().stream()
        .map(selection -> originals.get(selection.candidate().candidate().id())).toList();
    return new PersonalizedRankingResult(selectedBooks, selected.scoredById(),
        selected.mmrSelectionsById(), selected.explorationIds());
  }

  private static double cosine(List<Double> left, List<Double> right) {
    if (left == null || right == null || left.isEmpty() || left.size() != right.size()) return 0;
    double dot = 0;
    double leftNorm = 0;
    double rightNorm = 0;
    for (int index = 0; index < left.size(); index++) {
      if (left.get(index) == null || right.get(index) == null) return 0;
      double a = left.get(index);
      double b = right.get(index);
      if (!Double.isFinite(a) || !Double.isFinite(b)) return 0;
      dot += a * b;
      leftNorm += a * a;
      rightNorm += b * b;
    }
    return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
  }

  private CandidateSet buildLiveCandidateUnion(
      com.bookrush.recommendationservice.taste.UserTasteProfile profile,
      List<?> books, Map<String, AnalyticsScoreClient.BookScore> scores,
      Map<UUID, AnalyticsScoreClient.BookFeatureSnapshot> snapshots,
      List<BehaviorSignalsClient.Trending> trending) {
    var global = new ArrayList<CandidateUnionBuilder.Candidate>();
    var semantic = new ArrayList<CandidateUnionBuilder.Candidate>();
    var trendingById = trending.stream().collect(java.util.stream.Collectors.toMap(
        BehaviorSignalsClient.Trending::bookId, BehaviorSignalsClient.Trending::score,
        Math::max));
    var trendingCandidates = new ArrayList<CandidateUnionBuilder.Candidate>();
    for (Object value : books) {
      if (!(value instanceof Map<?, ?> raw) || raw.get("id") == null) continue;
      var book = new java.util.LinkedHashMap<String, Object>();
      raw.forEach((key, item) -> book.put(String.valueOf(key), item));
      String id = String.valueOf(raw.get("id"));
      double quality = scores.get(id) == null ? 0 : scores.get(id).score();
      global.add(new CandidateUnionBuilder.Candidate(book, quality));
      try {
        double trendScore = trendingById.getOrDefault(UUID.fromString(id), 0d);
        if (trendScore > 0) trendingCandidates.add(new CandidateUnionBuilder.Candidate(book, trendScore));
      } catch (IllegalArgumentException ignored) { }
      try {
        var snapshot = snapshots.get(UUID.fromString(id));
        if (snapshot != null && snapshot.embedding() != null
            && snapshot.embedding().vector() != null) {
          book.put("embedding", snapshot.embedding().vector());
          semantic.add(new CandidateUnionBuilder.Candidate(book, 0));
        }
      } catch (IllegalArgumentException ignored) {
        // Catalog IDs are validated later by the existing feed contract.
      }
    }
    var sources = new ArrayList<CandidateUnionBuilder.Source>();
    if (profile != null && profile.status() == com.bookrush.recommendationservice.taste.UserTasteProfile.Status.READY) {
      sources.add(new CandidateUnionBuilder.Source("semantic", semantic));
    }
    sources.add(new CandidateUnionBuilder.Source("global", global));
    sources.add(new CandidateUnionBuilder.Source("trending", trendingCandidates));
    return candidateUnionBuilder.build(profile, sources);
  }

  private static Object publicBook(Object book) {
    if (!(book instanceof Map<?, ?> raw) || !raw.containsKey("embedding")) return book;
    var copy = new java.util.LinkedHashMap<String, Object>();
    raw.forEach((key, value) -> {
      if (!"embedding".equals(String.valueOf(key))) copy.put(String.valueOf(key), value);
    });
    return copy;
  }

  private record PersonalizedRankingResult(List<?> books,
      Map<String, PersonalizedRankerV1.ScoredCandidate> scoredById,
      Map<String, BookDiversityMmr.Selection> selectionsById,
      Set<String> explorationIds) { }

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
    payload.put("viewable", true);
    payload.put("impressionId", impressionId);
    if (itemId != null) payload.put("recommendationItemId", itemId);
    behavior.publish("EXCERPT_IMPRESSION", bookId, payload,
        request.getHeader("Authorization"));
    return Map.of("impressionId", impressionId, "viewable", true);
  }
}
