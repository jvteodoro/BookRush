package com.bookrush.adminservice.reporting;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/reporting")
public class ReportingController {
  private final JdbcTemplate jdbc;

  public ReportingController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/reader-day")
  public Map<String, Object> readerDay(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    if (days < 1 || days > 366) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 366");
    }
    var rows = jdbc.queryForList("""
        SELECT day, population, platform, active_users, newly_active_users, sessions,
          weekly_active_users, monthly_active_users, returning_users,
          active_seconds, book_opens, meaningful_reads, completions,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.reader_day
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    var response = new LinkedHashMap<String, Object>();
    response.put("schemaVersion", "dashboard-response-v1");
    response.put("metricId", "reader_day");
    response.put("definitionVersion", "events-v1-untrusted-population");
    response.put("status", rows.isEmpty() ? "NO_DATA" : "UNTRUSTED");
    response.put("unit", "aggregate");
    response.put("from", Instant.now().minusSeconds(days * 86400L).atOffset(ZoneOffset.UTC).toString());
    response.put("to", Instant.now().atOffset(ZoneOffset.UTC).toString());
    response.put("population", "UNCLASSIFIED");
    response.put("sourceWatermark", rows.isEmpty() ? null : rows.getLast().get("source_watermark"));
    response.put("refreshedAt", rows.isEmpty() ? null : toInstant(rows.getLast().get("refreshed_at")));
    response.put("series", rows);
    return response;
  }

  private Instant toInstant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof Timestamp timestamp) return timestamp.toInstant();
    return value == null ? null : Instant.parse(String.valueOf(value));
  }

  @GetMapping("/health")
  public Object health(Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    return jdbc.queryForList("""
        SELECT projection_name, last_successful_refresh, source_watermark,
          record_count, definition_version, refresh_status, last_error_code
        FROM reporting.projection_health ORDER BY projection_name
        """);
  }

  @GetMapping("/feed-daily")
  public Map<String, Object> feedDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, feed_path, population, platform,
          served_count, viewable_count, attributed_open_count,
          attributed_read_10_count, attributed_read_50_count,
          attributed_complete_count, dwell_observations, dwell_seconds,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.feed_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, strategy, model_version, feed_path
        """, days);
    return response("feed_daily", "feed-attribution-v1-untrusted-population", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/pipeline")
  public Map<String, Object> pipeline(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, service, job_status, phase, status_count,
          oldest_pending_age_seconds, failure_count, throughput,
          calculation_version, source_watermark
        FROM reporting.pipeline_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at, service, phase, job_status
        """, days);
    return response("pipeline_snapshot", "analytics-pipeline-v1", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/pipeline-phases")
  public Map<String, Object> pipelinePhases(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, phase, phase_status, error_code, attempts, completed_count,
          failed_count, stuck_count, p50_duration_ms, p95_duration_ms,
          p99_duration_ms, calculation_version, refreshed_at, source_watermark
        FROM reporting.pipeline_phase_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, phase, phase_status, error_code
        """, days);
    return response("pipeline_phase_daily", "analytics-phase-v1", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/quality/attribution")
  public Map<String, Object> attributionQuality(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, attribution_status, outcome_count, unique_events,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.attribution_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, attribution_status
        """, days);
    return response("attribution_daily", "event-quality-v1", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/quality/reconciliation")
  public Map<String, Object> reconciliationQuality(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, ledger_impression_count, ledger_viewable_count,
          behavior_impression_event_count, behavior_unique_impression_count,
          mismatched_impression_count, duplicate_event_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.reconciliation_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("reconciliation_daily", "event-quality-v1", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/social-daily")
  public Map<String, Object> socialDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, follow_count, like_count, comment_count, share_count,
          book_opened_count, book_saved_count, unique_actors, reports_open,
          moderation_backlog, calculation_version, refreshed_at, source_watermark
        FROM reporting.social_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("social_daily", "product-activity-v1-untrusted-population", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/publisher-daily")
  public Map<String, Object> publisherDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, published_work_count, impressions, opens, reads, likes, shares, comments,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.publisher_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("publisher_daily", "product-activity-v1-untrusted-population", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/content-supply")
  public Map<String, Object> contentSupply(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, active_books, available_asset_versions,
          analyzed_asset_versions, eligible_excerpts, ranked_excerpts,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.content_supply_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at
        """, days);
    return response("content_supply_snapshot", "content-supply-v1", days,
        "UNCLASSIFIED", rows);
  }

  @GetMapping("/ingestion-daily")
  public Map<String, Object> ingestionDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, job_status, job_count, discovered_items, processed_items,
          succeeded_items, failed_items, item_status, item_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.ingestion_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, job_status, item_status
        """, days);
    return response("ingestion_daily", "ingestion-operations-v1", days,
        "UNCLASSIFIED", rows);
  }

  private Map<String, Object> response(String metricId, String definitionVersion, int days,
      String population, java.util.List<Map<String, Object>> rows) {
    var response = new LinkedHashMap<String, Object>();
    response.put("schemaVersion", "dashboard-response-v1");
    response.put("metricId", metricId);
    response.put("definitionVersion", definitionVersion);
    response.put("status", rows.isEmpty() ? "NO_DATA" : "UNTRUSTED");
    response.put("unit", "aggregate");
    response.put("from", Instant.now().minusSeconds(days * 86400L).atOffset(ZoneOffset.UTC).toString());
    response.put("to", Instant.now().atOffset(ZoneOffset.UTC).toString());
    response.put("population", population);
    response.put("sourceWatermark", rows.stream().map(row -> row.get("source_watermark"))
        .filter(java.util.Objects::nonNull).reduce((first, second) -> second).orElse(null));
    response.put("refreshedAt", rows.stream().map(row -> row.get("refreshed_at"))
        .filter(java.util.Objects::nonNull).reduce((first, second) -> second).orElse(null));
    response.put("series", rows);
    return response;
  }

  private void validateDays(int days) {
    if (days < 1 || days > 366) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 366");
    }
  }

  private void requireAnalyst(Authentication authentication, Principal principal) {
    if (principal == null || authentication == null || authentication.getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_platform-admins")
            || a.getAuthority().equals("ROLE_operators")
            || a.getAuthority().equals("ROLE_analysts")
            || a.getAuthority().equals("ROLE_platform-admin")
            || a.getAuthority().equals("ROLE_platform-operator")
            || a.getAuthority().equals("ROLE_ANALYST"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "reporting analyst permission required");
    }
  }
}
