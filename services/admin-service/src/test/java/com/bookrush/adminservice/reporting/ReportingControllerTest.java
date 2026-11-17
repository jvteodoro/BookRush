package com.bookrush.adminservice.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class ReportingControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ReportingController controller = new ReportingController(jdbc);
  private final Principal principal = () -> "analyst-1";

  @Test
  void rejectsReadersAndUnboundedQueries() {
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.readerDay(30, "PRODUCT", principal, reader)).getStatusCode().value());
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.readerDay(367, "PRODUCT", principal, analyst)).getStatusCode().value());
  }

  @Test
  void socialDailyIncludesBehaviorInteractionsWithoutIdentityFields() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-16"), "behavior_like_events", 2L,
        "behavior_unlike_events", 1L, "behavior_save_events", 3L,
        "behavior_unsave_events", 1L, "behavior_share_events", 1L,
        "behavior_comment_events", 1L, "behavior_unique_actors", 2L,
        "behavior_distinct_books", 1L)));

    var response = controller.socialDaily(7, principal, analyst);
    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals(2L, row.get("behavior_like_events"));
    assertEquals(1L, row.get("behavior_unlike_events"));
    assertFalse(row.containsKey("identity_subject"));
  }

  @Test
  void preservesUnknownPopulationInsteadOfGuessingHistoricProductData() {
    when(jdbc.queryForList(anyString(), eq(7), eq("UNKNOWN"))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "population", "UNKNOWN", "platform", "UNKNOWN",
        "refreshed_at", Timestamp.from(Instant.parse("2026-11-04T12:00:00Z")),
        "source_watermark", freshWatermark(),
        "active_users", 2L)));
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");

    var response = controller.readerDay(7, "UNKNOWN", principal, analyst);

    assertEquals("STALE", response.get("status"));
    assertEquals("UNKNOWN", response.get("population"));
  }

  @Test
  void reportsWeeklySignificantReadersAndPreviousWeekFromAuthenticatedPopulation() {
    when(jdbc.queryForMap(anyString(), eq("PRODUCT"))).thenReturn(Map.of(
        "weekly_significant_readers", 84L, "previous_week_significant_readers", 72L,
        "weekly_books_started", 154L, "previous_week_books_started", 139L,
        "weekly_sessions", 238L, "previous_week_sessions", 210L,
        "weekly_active_seconds", 298800L, "previous_week_active_seconds", 276000L,
        "event_count", 900L, "last_business_activity_at", Timestamp.from(Instant.now())));
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");

    var response = controller.weeklySignificantReaders("PRODUCT", principal, analyst);

    assertEquals("PRODUCT", response.get("population"));
    assertEquals("significant-read-v1-active-seconds-gte-60-weekly-unique-readers",
        response.get("definitionVersion"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals(84L, row.get("weekly_significant_readers"));
    assertEquals(72L, row.get("previous_week_significant_readers"));
  }

  @Test
  void exposesOnlyAggregatedCurrentLibraryStateToAnalysts() {
    when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
        "saved_book_pairs", 3L, "saved_books", 2L, "saved_subject_keys", 2L,
        "calculation_version", "library-current-state-v1-untrusted-population",
        "refreshed_at", Timestamp.from(Instant.now().minusSeconds(60))
    )));
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");

    var response = controller.libraryState(principal, analyst);

    assertEquals("current_snapshot", response.get("unit"));
    assertEquals("OK", response.get("status"));
    assertFalse(response.containsKey("from"));
    assertFalse(response.containsKey("to"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals(3L, row.get("saved_book_pairs"));
    assertEquals(2L, row.get("saved_books"));
    assertEquals(2L, row.get("saved_subject_keys"));

    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.libraryState(principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesFeedAndPipelineSnapshotsOnlyToReportingRoles() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7), eq("PRODUCT"))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"), "population", "PRODUCT",
        "refreshed_at", Timestamp.from(Instant.now().minusSeconds(60)),
        "strategy", "PERSONALIZED", "served_count", 4L,
        "viewable_count", 3L, "attributed_open_count", 1L,
        "source_watermark", freshWatermark())))
        .thenReturn(List.of(Map.of(
            "collected_at", Timestamp.from(Instant.parse("2026-11-04T12:00:00Z")),
            "phase", "ITEM", "job_status", "COMPLETED", "status_count", 4L,
            "failure_count", 0L, "throughput", 2L,
            "source_watermark", freshWatermark())));
    when(jdbc.queryForList(anyString(), eq("pipeline_snapshot"))).thenReturn(List.of(Map.of(
        "refreshed_at", Timestamp.from(Instant.now()), "source_watermark", freshWatermark(),
        "refresh_status", "SUCCESS")));

    assertEquals("OK", controller.feedDaily(7, "PRODUCT", principal, analyst).get("status"));
    assertEquals("NO_DATA", controller.pipeline(7, principal, analyst).get("status"));
    assertEquals("UNKNOWN_FRESHNESS", controller.attributionQuality(7, principal, analyst).get("status"));
    assertEquals("UNKNOWN_FRESHNESS", controller.reconciliationQuality(7, principal, analyst).get("status"));

    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.feedDaily(7, "PRODUCT", principal, reader)).getStatusCode().value());
  }

  @Test
  void returnsSampleSuppressedBookAndLanguageTrendsOnlyToAnalysts() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
        Map.entry("day", java.sql.Date.valueOf("2026-11-15")),
        Map.entry("aggregation_level", "BOOK"), Map.entry("aggregation_key", "book-1"),
        Map.entry("book_title", "Example"), Map.entry("language", "pt"),
        Map.entry("active_minutes", 30L), Map.entry("unique_readers", 5L),
        Map.entry("completions", 1L), Map.entry("period_active_minutes", 90L),
        Map.entry("entity_rank", 1L), Map.entry("source_watermark", freshWatermark()))));

    var response = controller.bookEngagementTrends(30, 5, 5, "PRODUCT", principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("book_engagement_trends", response.get("metricId"));
    assertEquals("PRODUCT", response.get("population"));
    assertEquals(1, ((List<?>) response.get("series")).size());
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.bookEngagementTrends(30, 5, 5, "PRODUCT", principal, reader))
        .getStatusCode().value());
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.bookEngagementTrends(30, 5, 11, "PRODUCT", principal, analyst))
        .getStatusCode().value());
  }

  @Test
  void exposesContentReadinessCountsAsUntrustedReportingData() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("collected_at", Timestamp.from(Instant.parse("2026-11-15T12:00:00Z"))),
        Map.entry("active_books", 2L), Map.entry("reader_ready_books", 1L),
        Map.entry("excerpt_ready_books", 1L), Map.entry("available_asset_versions", 2L),
        Map.entry("analyzed_asset_versions", 1L), Map.entry("eligible_excerpts", 1L),
        Map.entry("ranked_excerpts", 1L), Map.entry("calculation_version", "content-supply-v2"),
        Map.entry("refreshed_at", Timestamp.from(Instant.now().minusSeconds(60))),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.contentSupply(7, principal, analyst);

    assertEquals("OK", response.get("status"));
    assertEquals("content_supply_snapshot", response.get("metricId"));
    assertEquals(1L, ((Map<?, ?>) ((List<?>) response.get("series")).get(0))
        .get("reader_ready_books"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.contentSupply(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesAggregatedContentCoverageOnlyToAnalysts() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("language", "pt"), Map.entry("rights_status", "RIGHTS_ALLOWED"),
        Map.entry("active_books", 3L), Map.entry("reader_ready_books", 2L),
        Map.entry("eligible_excerpts", 8L), Map.entry("ranked_excerpts", 6L),
        Map.entry("calculation_version", "content-coverage-v1"),
        Map.entry("refreshed_at", Timestamp.from(Instant.now().minusSeconds(60))),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.contentCoverage(7, principal, analyst);

    assertEquals("OK", response.get("status"));
    assertEquals("content_coverage_snapshot", response.get("metricId"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals("pt", row.get("language"));
    assertEquals(8L, row.get("eligible_excerpts"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.contentCoverage(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesRankScoresWithoutCombiningRankerConfigurations() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("language", "pt"), Map.entry("rights_status", "RIGHTS_ALLOWED"),
        Map.entry("ranker_code", "ranker-a"), Map.entry("ranker_version", "v1"),
        Map.entry("configuration_hash", "a".repeat(64)), Map.entry("score_decile", 0),
        Map.entry("score_min", 0.2d), Map.entry("score_max", 0.4d),
        Map.entry("excerpt_count", 3L), Map.entry("calculation_version", "content-rank-score-deciles-v1"),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.contentRankScores(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("content_rank_score_snapshot", response.get("metricId"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals("ranker-a", row.get("ranker_code"));
    assertEquals(0, row.get("score_decile"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.contentRankScores(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesAggregateExcerptProductionWithoutEditorialStateInference() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("language", "pt"), Map.entry("rights_status", "RIGHTS_ALLOWED"),
        Map.entry("generation_method", "SENTENCE_WINDOW"), Map.entry("outcome", "RANKED"),
        Map.entry("reason_bucket", "NONE"), Map.entry("excerpt_count", 2L),
        Map.entry("calculation_version", "excerpt-production-v1"),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.excerptProduction(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("excerpt_production_snapshot", response.get("metricId"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals("RANKED", row.get("outcome"));
    assertEquals("NONE", row.get("reason_bucket"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.excerptProduction(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesImportQualityAggregatesOnlyToAnalysts() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("day", java.sql.Date.valueOf("2026-11-16")),
        Map.entry("source_code", "GUTENBERG"), Map.entry("item_status", "FAILED"),
        Map.entry("error_bucket", "ASSET"), Map.entry("rights_outcome", "NOT_CONFIRMED"),
        Map.entry("asset_outcome", "NO_ASSET"), Map.entry("item_count", 1L),
        Map.entry("retry_attempt_count", 1L),
        Map.entry("calculation_version", "import-quality-v1"),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.importQualityDaily(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("import_quality_daily", response.get("metricId"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals("ASSET", row.get("error_bucket"));
    assertEquals(1L, row.get("retry_attempt_count"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.importQualityDaily(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesBacklogAndProjectionFreshnessOnlyToAnalysts() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("source_type", "ANALYTICS"), Map.entry("queue_name", "analysis_job_item"),
        Map.entry("pending_count", 2L), Map.entry("oldest_pending_age_seconds", 1200L),
        Map.entry("failed_count", 1L), Map.entry("stuck_count", 1L),
        Map.entry("freshness_age_seconds", 60L), Map.entry("refresh_status", "SUCCESS"),
        Map.entry("record_count", 8L), Map.entry("source_watermark", freshWatermark()))));

    var response = controller.backlog(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("backlog_snapshot", response.get("metricId"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals(1200L, row.get("oldest_pending_age_seconds"));
    assertEquals(1L, row.get("stuck_count"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.backlog(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesIngestionQueueSnapshotsOnlyToReportingRoles() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("collected_at", Timestamp.from(Instant.parse("2026-11-15T12:00:00Z"))),
        Map.entry("queue_status", "PENDING"), Map.entry("task_count", 3L),
        Map.entry("oldest_task_age_seconds", 1200L),
        Map.entry("expired_lease_count", 0L),
        Map.entry("calculation_version", "ingestion-queue-v1"),
        Map.entry("refreshed_at", Timestamp.from(Instant.now().minusSeconds(60))),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.ingestionQueue(7, principal, analyst);

    assertEquals("OK", response.get("status"));
    assertEquals("ingestion_queue_snapshot", response.get("metricId"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals("PENDING", row.get("queue_status"));
    assertEquals(1200L, row.get("oldest_task_age_seconds"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.ingestionQueue(7, principal, reader)).getStatusCode().value());
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.ingestionQueue(0, principal, analyst)).getStatusCode().value());
  }

  @Test
  void comparesEqualExecutivePeriodsWithoutInferringMissingDataAsZero() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    Map<String, Object> uniqueReaders = Map.of("has_data", true, "daily_peak_users", 5L);
    Map<String, Object> reader = Map.of("has_data", true,
        "sessions", 8L, "active_seconds", 600L, "meaningful_reads", 3L);
    Map<String, Object> feed = Map.of("has_data", true, "served", 10L, "viewable", 8L,
        "opened", 4L, "meaningful", 2L, "complete", 1L);
    when(jdbc.queryForMap(anyString(), any(Object[].class)))
        .thenReturn(uniqueReaders, reader, feed, uniqueReaders, reader, feed);

    var response = controller.executiveSummary(7, "PRODUCT", principal, analyst);

    assertEquals("OK", response.get("status"));
    assertEquals("executive-period-comparison-v3-issuer-subject-population",
        response.get("definitionVersion"));
    assertEquals("current", ((Map<?, ?>) response.get("current")).get("label"));
    assertEquals("previous", ((Map<?, ?>) response.get("previous")).get("label"));
    assertEquals(5L, ((Map<?, ?>) response.get("current")).get("daily_peak_users"));
    assertEquals(2, ((List<?>) response.get("series")).size());
  }

  @Test
  void exposesEditorialQualityAsUntrustedAggregate() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    var row = Map.<String, Object>of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "campaigns", 1L, "sampled_items", 4L, "pending_items", 1L,
        "submitted_annotations", 3L, "unique_annotators", 2L,
        "disputed_items", 1L, "p50_elapsed_ms", 1200.0,
        "source_watermark", freshWatermark());
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(row));

    var response = controller.editorialQualityDaily(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("UNKNOWN", response.get("population"));
    assertEquals(1, ((List<?>) response.get("series")).size());
  }

  @Test
  void exposesProgressCohortAndLibraryActionsAsUntrustedAggregate() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "reader_book_pairs_started", 5L, "reader_book_pairs_10", 4L,
        "reader_book_pairs_50", 2L,
        "reader_book_pairs_100", 1L, "save_actions", 3L,
        "unsave_actions", 1L, "unique_savers", 2L, "unique_unsavers", 1L,
        "source_watermark", freshWatermark())));

    var response = controller.readingProgressDaily(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("UNKNOWN", response.get("population"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals(5L, row.get("reader_book_pairs_started"));
    assertEquals(4L, row.get("reader_book_pairs_10"));
    assertEquals(1L, row.get("unsave_actions"));

    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.readingProgressDaily(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesDailyReadingHabitsAsUntrustedAggregate() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-16"), "active_readers", 2L,
        "reading_minutes", 10L, "completed_books", 1L,
        "streak_1_day_users", 0L, "streak_2_6_day_users", 1L,
        "streak_7_29_day_users", 1L, "streak_30_plus_day_users", 0L,
        "source_watermark", freshWatermark())));

    var response = controller.readingHabitsDaily(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals(1L, row.get("streak_2_6_day_users"));
    assertEquals(1L, row.get("streak_7_29_day_users"));

    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.readingHabitsDaily(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void exposesRightsDecisionsAndPublisherAttestationsAsDistinctAggregate() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-16"),
        "source_type", "CATALOG_DECISION", "right_type", "DISTRIBUTION",
        "territory", "GLOBAL", "status", "ACTIVE", "item_count", 1L,
        "source_watermark", freshWatermark())));

    var response = controller.rightsStatusDaily(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("UNKNOWN", response.get("population"));
    var row = (Map<?, ?>) ((List<?>) response.get("series")).get(0);
    assertEquals("CATALOG_DECISION", row.get("source_type"));
    assertEquals("ACTIVE", row.get("status"));
    assertFalse(row.containsKey("evidence_reference"));

    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.rightsStatusDaily(7, principal, reader)).getStatusCode().value());
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.rightsStatusDaily(367, principal, analyst)).getStatusCode().value());
  }

  @Test
  void protectsExcerptPerformanceWithSampleAndPageLimits() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.excerptPerformance(30, 0, 200, 0, principal, analyst))
        .getStatusCode().value());
    when(jdbc.queryForList(anyString(), eq(7), eq(20), eq(51), eq(0))).thenReturn(List.of(Map.ofEntries(
        Map.entry("day", java.sql.Date.valueOf("2026-11-01")),
        Map.entry("excerpt_id", "excerpt-1"), Map.entry("book_id", "book-1"),
        Map.entry("eligible", true), Map.entry("impressions", 20L),
        Map.entry("viewable_count", 15L), Map.entry("attributed_open_count", 4L),
        Map.entry("attributed_read_count", 2L), Map.entry("attributed_complete_count", 1L),
        Map.entry("unique_subjects", 10L),
        Map.entry("source_watermark", freshWatermark()))));
    var response = controller.excerptPerformance(7, 20, 50, 0, principal, analyst);
    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("SUPPRESSED_BELOW_MIN_SAMPLE", response.get("population"));
    assertEquals(1, ((List<?>) response.get("series")).size());
    assertEquals(false, response.get("hasNext"));
  }

  @Test
  void paginatesLargeExcerptQueriesWithStableMetadata() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7), eq(20), eq(3), eq(2))).thenReturn(List.of(
        Map.of("excerpt_id", "excerpt-3"), Map.of("excerpt_id", "excerpt-4"),
        Map.of("excerpt_id", "excerpt-5")));

    var response = controller.excerptPerformance(7, 20, 2, 1, principal, analyst);

    assertEquals(1, response.get("page"));
    assertEquals(2, response.get("pageSize"));
    assertEquals(true, response.get("hasNext"));
    assertEquals(2, ((List<?>) response.get("series")).size());
  }

  @Test
  void exposesExperimentGuardrailsOnlyAsObservationalAggregates() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.ofEntries(
        Map.entry("day", java.sql.Date.valueOf("2026-11-01")),
        Map.entry("experiment_id", "exp-1"), Map.entry("variant", "CONTROL"),
        Map.entry("assigned_subjects", 10L), Map.entry("request_count", 10L),
        Map.entry("impression_count", 20L), Map.entry("viewable_count", 15L),
        Map.entry("attributed_open_count", 3L), Map.entry("attributed_read_count", 2L),
        Map.entry("attributed_complete_count", 1L), Map.entry("unique_subjects", 10L),
        Map.entry("source_watermark", freshWatermark()))));

    var response = controller.experimentDaily(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals("UNKNOWN", response.get("population"));
  }

  @Test
  void exposesAccessAuditWithoutActorOrTargetValues() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"), "action", "REPORTING_READ",
        "event_count", 4L, "unique_actors", 2L, "unique_targets", 1L,
        "source_watermark", freshWatermark())));

    var response = controller.accessAudit(7, principal, analyst);

    assertEquals("UNKNOWN_FRESHNESS", response.get("status"));
    assertEquals(1, ((List<?>) response.get("series")).size());
    org.junit.jupiter.api.Assertions.assertFalse(
        ((Map<?, ?>) ((List<?>) response.get("series")).get(0)).containsKey("actor_subject"));
  }

  @Test
  void marksOldProjectionDataAsStale() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7), eq("PRODUCT"))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "refreshed_at", Timestamp.from(Instant.now().minusSeconds(3600)),
        "source_watermark", Timestamp.from(Instant.now().minusSeconds(7200)))));

    var response = controller.readerDay(7, "PRODUCT", principal, analyst);

    assertEquals("STALE", response.get("status"));
    long freshness = ((Number) response.get("freshnessSeconds")).longValue();
    assertTrue(freshness >= 3600L && freshness <= 3605L);
  }

  private static Timestamp freshWatermark() {
    return Timestamp.from(Instant.now().minusSeconds(60));
  }
}
