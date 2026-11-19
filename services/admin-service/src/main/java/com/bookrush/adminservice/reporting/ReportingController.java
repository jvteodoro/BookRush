package com.bookrush.adminservice.reporting;

import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/reporting")
public class ReportingController {
  private static final int EXPORT_ROW_LIMIT = 10_000;
  private final JdbcTemplate jdbc;
  @Value("${bookrush.reporting.stale-after-seconds:900}")
  private long staleAfterSeconds = 900;

  public ReportingController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private record ExportSpec(List<String> columns, String sql) {}

  public record ReleaseEventRequest(
      @NotBlank @Size(max = 220) @Pattern(regexp = "[A-Za-z0-9._:/#-]+") String eventId,
      @NotBlank @Size(max = 180) String jobName,
      @Min(1) long buildNumber,
      @Size(max = 1024) String buildUrl,
      @Size(max = 180) String branch,
      @Pattern(regexp = "([0-9a-fA-F]{40}|[0-9a-fA-F]{64})") String gitCommit,
      @NotBlank @Pattern(regexp = "BUILD|DEPLOY|ROLLBACK") String eventType,
      @NotBlank @Pattern(regexp = "STARTED|SUCCESS|FAILURE|UNSTABLE|ABORTED") String status,
      @Size(max = 120) String serviceName,
      @Size(max = 512) String imageRef,
      @Pattern(regexp = "sha256:[0-9a-f]{64}") String imageDigest,
      @Min(0) @Max(86400000) Long durationMs,
      @NotNull Instant occurredAt) {}

  @PostMapping("/internal/release-events")
  public Map<String, Object> ingestReleaseEvent(@Valid @RequestBody ReleaseEventRequest event,
      Authentication authentication) {
    requireReleaseWriter(authentication);
    validateReleaseEvent(event);
    int accepted = jdbc.update("""
        INSERT INTO reporting.release_event(
          id, event_id, job_name, build_number, build_url, branch, git_commit,
          event_type, status, service_name, image_ref, image_digest, duration_ms, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (event_id) DO UPDATE SET event_id = EXCLUDED.event_id
        WHERE reporting.release_event.job_name IS NOT DISTINCT FROM EXCLUDED.job_name
          AND reporting.release_event.build_number IS NOT DISTINCT FROM EXCLUDED.build_number
          AND reporting.release_event.build_url IS NOT DISTINCT FROM EXCLUDED.build_url
          AND reporting.release_event.branch IS NOT DISTINCT FROM EXCLUDED.branch
          AND reporting.release_event.git_commit IS NOT DISTINCT FROM EXCLUDED.git_commit
          AND reporting.release_event.event_type IS NOT DISTINCT FROM EXCLUDED.event_type
          AND reporting.release_event.status IS NOT DISTINCT FROM EXCLUDED.status
          AND reporting.release_event.service_name IS NOT DISTINCT FROM EXCLUDED.service_name
          AND reporting.release_event.image_ref IS NOT DISTINCT FROM EXCLUDED.image_ref
          AND reporting.release_event.image_digest IS NOT DISTINCT FROM EXCLUDED.image_digest
          AND reporting.release_event.duration_ms IS NOT DISTINCT FROM EXCLUDED.duration_ms
        """, UUID.randomUUID(), event.eventId(), event.jobName(), event.buildNumber(),
        event.buildUrl(), event.branch(), event.gitCommit(), event.eventType(), event.status(),
        event.serviceName(), event.imageRef(), event.imageDigest(), event.durationMs(),
        Timestamp.from(event.occurredAt()));
    if (accepted == 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "eventId was already used with different release metadata");
    var stored = jdbc.queryForMap("""
        SELECT event_id, status, occurred_at
        FROM reporting.release_event WHERE event_id = ?
        """, event.eventId());
    return Map.of("eventId", stored.get("event_id"), "accepted", true,
        "status", stored.get("status"), "occurredAt", stored.get("occurred_at"));
  }

  @GetMapping("/release-events")
  public Map<String, Object> releaseEvents(@RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "100") int limit, Principal principal,
      Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    if (limit < 1 || limit > 500)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
    var rows = jdbc.queryForList("""
        SELECT event_id, job_name, build_number, build_url, branch, git_commit,
          event_type, status, service_name, image_ref, image_digest, duration_ms, occurred_at
        FROM reporting.release_event
        WHERE occurred_at >= now() - (? * INTERVAL '1 day')
        ORDER BY occurred_at DESC, id DESC
        LIMIT ?
        """, days, limit);
    var result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "dashboard-response-v1");
    result.put("metricId", "release_events");
    result.put("definitionVersion", "jenkins-release-events-v1");
    result.put("status", rows.isEmpty() ? "NO_DATA" : "OK");
    result.put("unit", "events");
    result.put("from", Instant.now().minusSeconds(days * 86400L).atOffset(ZoneOffset.UTC).toString());
    result.put("to", Instant.now().atOffset(ZoneOffset.UTC).toString());
    result.put("population", "PLATFORM");
    result.put("warnings", List.of());
    result.put("series", rows);
    return result;
  }

  private void validateReleaseEvent(ReleaseEventRequest event) {
    if ("BUILD".equals(event.eventType()) && event.serviceName() != null
        || !"BUILD".equals(event.eventType()) && (event.serviceName() == null || event.serviceName().isBlank()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "serviceName must be absent for BUILD and present for DEPLOY/ROLLBACK");
  }

  private void requireReleaseWriter(Authentication authentication) {
    if (authentication == null || authentication.getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals("SCOPE_bookrush.reporting.release.write")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "release reporting scope required");
  }

  @GetMapping(value = "/export/{dataset}", produces = "text/csv")
  public ResponseEntity<byte[]> exportReporting(@PathVariable String dataset,
      @RequestParam(defaultValue = "30") int days, Principal principal,
      Authentication authentication) {
    requireOperator(authentication, principal);
    validateDays(days);
    ExportSpec spec = exportSpec(dataset);
    var rows = jdbc.queryForList(spec.sql(), days, EXPORT_ROW_LIMIT + 1);
    if (rows.size() > EXPORT_ROW_LIMIT) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
          "Export exceeds the 10000-row limit; narrow the date range");
    }
    byte[] csv = csvBytes(spec.columns(), rows);
    jdbc.update("""
        INSERT INTO admin.audit_log(id, actor_subject, action, target, details)
        VALUES (?, ?, 'REPORTING_DATA_EXPORTED', ?,
          jsonb_build_object('days', ?::int, 'rowCount', ?::int))
        """, UUID.randomUUID(), principal.getName(), dataset, days, rows.size());
    String filename = "bookrush-" + dataset + "-" + days + "d.csv";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename(filename, StandardCharsets.UTF_8).build());
    headers.setCacheControl("no-store");
    return new ResponseEntity<>(csv, headers, HttpStatus.OK);
  }

  private ExportSpec exportSpec(String dataset) {
    return switch (dataset) {
      case "reader-day" -> new ExportSpec(List.of("day", "population", "platform",
          "active_users", "newly_active_users", "sessions", "weekly_active_users",
          "monthly_active_users", "returning_users", "active_seconds", "book_opens",
          "meaningful_reads", "completions", "calculation_version", "refreshed_at",
          "source_watermark"), """
          SELECT day, population, platform, active_users, newly_active_users, sessions,
            weekly_active_users, monthly_active_users, returning_users, active_seconds,
            book_opens, meaningful_reads, completions, calculation_version,
            refreshed_at, source_watermark
          FROM reporting.reader_day WHERE day >= current_date - (? - 1)
          ORDER BY day, population, platform LIMIT ?
          """);
      case "feed-daily" -> new ExportSpec(List.of("day", "model_version", "strategy",
          "feed_path", "population", "platform", "served_count", "mature_served_count",
          "viewable_count", "eligible_viewable_count", "attributed_open_count",
          "mature_attributed_open_count", "mature_attributed_significant_read_count",
          "mature_attributed_complete_count", "attributed_read_10_count",
          "attributed_read_50_count", "attributed_complete_count", "dwell_observations",
          "dwell_seconds", "skipped_visits", "calculation_version", "refreshed_at",
          "source_watermark"), """
          SELECT day, model_version, strategy, feed_path, population, platform, served_count,
            mature_served_count, viewable_count, eligible_viewable_count, attributed_open_count,
            mature_attributed_open_count, mature_attributed_significant_read_count,
            mature_attributed_complete_count, attributed_read_10_count,
            attributed_read_50_count, attributed_complete_count, dwell_observations,
            dwell_seconds, skipped_visits, calculation_version, refreshed_at, source_watermark
          FROM reporting.feed_daily WHERE day >= current_date - (? - 1)
          ORDER BY day, strategy, model_version, feed_path, platform LIMIT ?
          """);
      case "recommendation-daily" -> new ExportSpec(List.of("day", "model_version",
          "strategy", "feed_path", "population", "platform", "request_count",
          "candidate_count", "scored_candidate_count", "selected_count", "item_count",
          "impression_count", "viewable_count", "repeat_viewable_count",
          "viewable_unique_books", "unique_books", "p50_rank", "p90_rank",
          "calculation_version", "refreshed_at", "source_watermark"), """
          SELECT day, model_version, strategy, feed_path, population, platform, request_count,
            candidate_count, scored_candidate_count, selected_count, item_count,
            impression_count, viewable_count, repeat_viewable_count,
            viewable_unique_books, unique_books, p50_rank, p90_rank,
            calculation_version, refreshed_at, source_watermark
          FROM reporting.recommendation_daily WHERE day >= current_date - (? - 1)
          ORDER BY day, strategy, model_version, feed_path, platform LIMIT ?
          """);
      case "recommendation-quality" -> new ExportSpec(List.of("day", "model_version",
          "strategy", "outcome_type", "attributed_count", "unique_subjects", "unique_books",
          "p50_rank", "calculation_version", "refreshed_at", "source_watermark"), """
          SELECT day, model_version, strategy, outcome_type, attributed_count, unique_subjects,
            unique_books, p50_rank, calculation_version, refreshed_at, source_watermark
          FROM reporting.recommendation_quality_daily WHERE day >= current_date - (? - 1)
          ORDER BY day, strategy, model_version, outcome_type LIMIT ?
          """);
      case "recommendation-profile-quality" -> new ExportSpec(List.of("day", "model_version",
          "strategy", "feed_path", "population", "platform", "confidence_bucket",
          "profile_size_bucket", "fatigue_coverage_bucket", "cold_start", "request_count",
          "calculation_version", "refreshed_at", "source_watermark"), """
          SELECT day, model_version, strategy, feed_path, population, platform,
            confidence_bucket, profile_size_bucket, fatigue_coverage_bucket, cold_start,
            request_count, calculation_version, refreshed_at, source_watermark
          FROM reporting.recommendation_profile_quality_daily
          WHERE day >= current_date - (? - 1)
          ORDER BY day, strategy, model_version, feed_path, confidence_bucket,
            profile_size_bucket, fatigue_coverage_bucket, cold_start LIMIT ?
          """);
      case "moderation-daily" -> new ExportSpec(List.of("day", "open_count", "in_review_count",
          "resolved_count", "dismissed_count", "p50_resolution_seconds",
          "p95_resolution_seconds", "oldest_open_age_seconds", "open_under_1_day",
          "open_1_to_3_days", "open_3_to_7_days", "open_7_days_or_more",
          "calculation_version", "refreshed_at", "source_watermark"), """
          SELECT day, open_count, in_review_count, resolved_count, dismissed_count,
            p50_resolution_seconds, p95_resolution_seconds, oldest_open_age_seconds,
            open_under_1_day, open_1_to_3_days, open_3_to_7_days, open_7_days_or_more,
            calculation_version, refreshed_at, source_watermark
          FROM reporting.moderation_daily WHERE day >= current_date - (? - 1)
          ORDER BY day LIMIT ?
          """);
      case "social-daily" -> new ExportSpec(List.of("day", "follow_count", "like_count",
          "comment_count", "share_count", "book_opened_count", "book_saved_count",
          "unique_actors", "reports_open", "moderation_backlog", "behavior_like_events",
          "behavior_unlike_events", "behavior_save_events", "behavior_unsave_events",
          "behavior_share_events", "behavior_comment_events", "behavior_unique_actors",
          "behavior_distinct_books", "calculation_version", "refreshed_at", "source_watermark"), """
          SELECT day, follow_count, like_count, comment_count, share_count, book_opened_count,
            book_saved_count, unique_actors, reports_open, moderation_backlog,
            behavior_like_events, behavior_unlike_events, behavior_save_events,
            behavior_unsave_events, behavior_share_events, behavior_comment_events,
            behavior_unique_actors, behavior_distinct_books, calculation_version,
            refreshed_at, source_watermark
          FROM reporting.social_daily WHERE day >= current_date - (? - 1)
          ORDER BY day LIMIT ?
          """);
      case "book-engagement" -> new ExportSpec(List.of("day", "aggregation_level",
          "aggregation_key", "book_id", "book_title", "language", "impressions",
          "viewable_count", "opens", "reads", "active_minutes", "unique_readers",
          "completions", "interactions", "calculation_version", "refreshed_at",
          "source_watermark"), """
          SELECT day, aggregation_level, aggregation_key, book_id, book_title, language,
            impressions, viewable_count, opens, reads, active_minutes, unique_readers,
            completions, interactions, calculation_version, refreshed_at, source_watermark
          FROM reporting.book_engagement_daily
          WHERE day >= current_date - (? - 1) AND unique_readers >= 5
          ORDER BY day DESC, aggregation_level, unique_readers DESC, aggregation_key LIMIT ?
          """);
      case "rights-status-daily" -> new ExportSpec(List.of("day", "source_type", "right_type",
          "territory", "status", "item_count", "calculation_version", "refreshed_at",
          "source_watermark"), """
          SELECT day, source_type, right_type, territory, status, item_count,
            calculation_version, refreshed_at, source_watermark
          FROM reporting.rights_status_daily WHERE day >= current_date - (? - 1)
          ORDER BY day, source_type, right_type, territory, status LIMIT ?
          """);
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Unsupported reporting export dataset");
    };
  }

  private byte[] csvBytes(List<String> columns, List<Map<String, Object>> rows) {
    StringBuilder csv = new StringBuilder();
    appendCsvRow(csv, columns);
    for (Map<String, Object> row : rows) {
      appendCsvRow(csv, columns.stream().map(row::get).toList());
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void appendCsvRow(StringBuilder csv, List<?> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) csv.append(',');
      Object value = values.get(i);
      String cell = value == null ? "" : value instanceof Timestamp timestamp
          ? timestamp.toInstant().toString()
          : value instanceof OffsetDateTime offsetDateTime
              ? offsetDateTime.toInstant().toString()
              : value instanceof Instant instant ? instant.toString() : String.valueOf(value);
      if (value instanceof String) {
        String trimmed = cell.stripLeading();
        if (!trimmed.isEmpty() && "=+@-".indexOf(trimmed.charAt(0)) >= 0) cell = "'" + cell;
      }
      csv.append('"').append(cell.replace("\"", "\"\"")).append('"');
    }
    csv.append("\r\n");
  }

  private void requireOperator(Authentication authentication, Principal principal) {
    if (principal == null || authentication == null || authentication.getAuthorities().stream()
        .noneMatch(authority -> authority.getAuthority().equals("ROLE_platform-admins")
            || authority.getAuthority().equals("ROLE_operators")
            || authority.getAuthority().equals("ROLE_platform-admin")
            || authority.getAuthority().equals("ROLE_platform-operator")
            || authority.getAuthority().equals("ROLE_OPERATOR"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "reporting export operator permission required");
    }
  }

  @GetMapping("/reader-day")
  public Map<String, Object> readerDay(@RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "PRODUCT") String population,
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
        WHERE day >= current_date - (? - 1) AND population = ?
        ORDER BY day
        """, days, validatePopulation(population));
    return response("reader_day", "events-issuer-subject-population-platform-significant-read-v4", days,
        population, rows);
  }

  @GetMapping("/weekly-significant-readers")
  public Map<String, Object> weeklySignificantReaders(
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    String selectedPopulation = validatePopulation(population);
    var row = jdbc.queryForMap("""
        SELECT count(DISTINCT (identity_issuer, identity_subject)) FILTER (
            WHERE event_type = 'READ_SESSION'
              AND payload->>'activeSeconds' ~ '^[0-9]+$'
              AND (payload->>'activeSeconds')::bigint >= 60
              AND occurred_at >= now() - INTERVAL '7 days') AS weekly_significant_readers,
          count(DISTINCT (identity_issuer, identity_subject, book_id)) FILTER (
            WHERE event_type = 'BOOK_OPEN' AND book_id IS NOT NULL
              AND occurred_at >= now() - INTERVAL '7 days') AS weekly_books_started,
          count(*) FILTER (WHERE event_type = 'READ_SESSION'
            AND occurred_at >= now() - INTERVAL '7 days') AS weekly_sessions,
          count(DISTINCT (identity_issuer, identity_subject)) FILTER (
            WHERE event_type = 'READ_SESSION'
              AND payload->>'activeSeconds' ~ '^[0-9]+$'
              AND (payload->>'activeSeconds')::bigint >= 60
              AND occurred_at < now() - INTERVAL '7 days') AS previous_week_significant_readers,
          count(DISTINCT (identity_issuer, identity_subject, book_id)) FILTER (
            WHERE event_type = 'BOOK_OPEN' AND book_id IS NOT NULL
              AND occurred_at < now() - INTERVAL '7 days') AS previous_week_books_started,
          count(*) FILTER (WHERE event_type = 'READ_SESSION'
            AND occurred_at < now() - INTERVAL '7 days') AS previous_week_sessions,
          coalesce(sum(CASE WHEN event_type = 'READ_SESSION'
            AND payload->>'activeSeconds' ~ '^[0-9]+$'
            AND occurred_at >= now() - INTERVAL '7 days'
            THEN LEAST((payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0)
            AS weekly_active_seconds,
          coalesce(sum(CASE WHEN event_type = 'READ_SESSION'
            AND payload->>'activeSeconds' ~ '^[0-9]+$'
            AND occurred_at < now() - INTERVAL '7 days'
            THEN LEAST((payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0)
            AS previous_week_active_seconds,
          count(*) FILTER (WHERE occurred_at >= now() - INTERVAL '7 days') AS event_count,
          max(occurred_at) FILTER (WHERE occurred_at >= now() - INTERVAL '7 days') AS last_business_activity_at
        FROM behavior.events
        WHERE identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
          AND coalesce(identity_population, 'UNKNOWN') = ?
          AND occurred_at >= now() - INTERVAL '14 days'
          AND event_type IN ('READ_SESSION', 'BOOK_OPEN', 'READ_PROGRESS',
            'BOOK_COMPLETE', 'READ_COMPLETE')
        """, selectedPopulation);
    var result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "dashboard-response-v1");
    result.put("metricId", "weekly_significant_readers");
    result.put("definitionVersion", "significant-read-v1-active-seconds-gte-60-weekly-unique-readers");
    result.put("status", ((Number) row.get("event_count")).longValue() == 0 ? "NO_DATA" : "OK");
    result.put("unit", "unique_readers");
    result.put("population", selectedPopulation);
    result.put("periodDays", 7);
    result.put("thresholdActiveSeconds", 60);
    result.put("lastBusinessActivityAt", row.get("last_business_activity_at"));
    result.put("series", List.of(row));
    return result;
  }

  private String validatePopulation(String population) {
    if (!List.of("PRODUCT", "ADMIN_SEED", "TEST", "UNKNOWN").contains(population)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported population");
    }
    return population;
  }

  @GetMapping("/executive-summary")
  public Map<String, Object> executiveSummary(@RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    String selectedPopulation = validatePopulation(population);
    var current = executivePeriod(days, false, selectedPopulation);
    var previous = executivePeriod(days, true, selectedPopulation);
    var response = new LinkedHashMap<String, Object>();
    response.put("schemaVersion", "dashboard-response-v1");
    response.put("metricId", "executive_summary");
    response.put("definitionVersion", "executive-period-comparison-v3-issuer-subject-population");
    response.put("status", Boolean.TRUE.equals(current.get("has_data")) ? "OK" : "NO_DATA");
    response.put("unit", "period_aggregate");
    response.put("population", selectedPopulation);
    response.put("days", days);
    response.put("current", current);
    response.put("previous", previous);
    response.put("series", java.util.List.of(current, previous));
    return response;
  }

  private Map<String, Object> executivePeriod(int days, boolean previous, String population) {
    String readerWindow = previous
        ? "day >= current_date - (2 * ? - 1) AND day < current_date - (? - 1)"
        : "day >= current_date - (? - 1)";
    String feedWindow = previous
        ? "day >= current_date - (2 * ? - 1) AND day < current_date - (? - 1)"
        : "day >= current_date - (? - 1)";
    var uniqueReaders = jdbc.queryForMap("""
        SELECT count(daily_users) > 0 AS has_data, max(daily_users) AS daily_peak_users
        FROM (
          SELECT occurred_at::date AS day,
            count(DISTINCT (identity_issuer, identity_subject)) AS daily_users
          FROM behavior.events
          WHERE identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
            AND coalesce(identity_population, 'UNKNOWN') = ?
            AND event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
              'BOOK_COMPLETE', 'READ_COMPLETE', 'BOOK_LIKE', 'BOOK_UNLIKE',
              'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
            AND %s
          GROUP BY occurred_at::date
        ) daily
        """.formatted(previous
            ? "occurred_at >= current_date - (2 * ? - 1) AND occurred_at < current_date - (? - 1)"
            : "occurred_at >= current_date - (? - 1) AND occurred_at < current_date + INTERVAL '1 day'"),
        previous ? new Object[] {population, days, days} : new Object[] {population, days});
    var reader = jdbc.queryForMap("""
        SELECT count(*) > 0 AS has_data,
          sum(sessions) AS sessions, sum(active_seconds) AS active_seconds,
          sum(meaningful_reads) AS meaningful_reads
        FROM reporting.reader_day
        """ + " WHERE " + readerWindow + " AND population = ?",
        previous ? new Object[] {days, days, population} : new Object[] {days, population});
    var feed = jdbc.queryForMap("""
        SELECT count(*) > 0 AS has_data,
          sum(served_count) AS served, sum(viewable_count) AS viewable,
          sum(attributed_open_count) AS opened,
          sum(attributed_read_10_count) AS meaningful,
          sum(attributed_complete_count) AS complete
        FROM reporting.feed_daily
        """ + " WHERE " + feedWindow + " AND population = ?",
        previous ? new Object[] {days, days, population} : new Object[] {days, population});
    var period = new LinkedHashMap<String, Object>();
    period.put("label", previous ? "previous" : "current");
    period.put("has_data", Boolean.TRUE.equals(uniqueReaders.get("has_data"))
        || Boolean.TRUE.equals(reader.get("has_data")) || Boolean.TRUE.equals(feed.get("has_data")));
    period.put("daily_peak_users", uniqueReaders.get("daily_peak_users"));
    period.put("sessions", reader.get("sessions"));
    period.put("active_seconds", reader.get("active_seconds"));
    period.put("meaningful_reads", reader.get("meaningful_reads"));
    period.put("served", feed.get("served"));
    period.put("viewable", feed.get("viewable"));
    period.put("opened", feed.get("opened"));
    period.put("meaningful", feed.get("meaningful"));
    period.put("complete", feed.get("complete"));
    return period;
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
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, feed_path, population, platform,
          served_count, viewable_count, eligible_viewable_count, attributed_open_count,
          mature_attributed_open_count, mature_served_count,
          mature_attributed_significant_read_count, mature_attributed_complete_count,
          attributed_read_10_count, attributed_read_50_count,
          attributed_complete_count, dwell_observations, dwell_seconds, skipped_visits,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.feed_daily
        WHERE day >= current_date - (? - 1) AND population = ?
        ORDER BY day, strategy, model_version, feed_path
        """, days, validatePopulation(population));
    return response("feed_daily", "feed-attribution-v5-population-significant-read", days,
        population, rows);
  }

  @GetMapping("/recommendation-daily")
  public Map<String, Object> recommendationDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, feed_path, population, platform,
          request_count, candidate_count, scored_candidate_count, selected_count,
          item_count, impression_count, viewable_count,
          repeat_viewable_count, viewable_unique_books, unique_books, p50_rank, p90_rank,
          calculation_version,
          refreshed_at, source_watermark
        FROM reporting.recommendation_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, strategy, model_version, feed_path
        """, days);
    return response("recommendation_daily", "recommendation-ledger-v4-candidate-funnel-repeat-viewable-30d-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/recommendation-profile-quality-daily")
  public Map<String, Object> recommendationProfileQualityDaily(
      @RequestParam(defaultValue = "30") int days, Principal principal,
      Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, feed_path, population, platform,
          confidence_bucket, profile_size_bucket, fatigue_coverage_bucket, cold_start,
          request_count, calculation_version, refreshed_at, source_watermark
        FROM reporting.recommendation_profile_quality_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, strategy, model_version, feed_path, confidence_bucket,
          profile_size_bucket, fatigue_coverage_bucket, cold_start
        """, days);
    return response("recommendation_profile_quality_daily",
        "recommendation-profile-quality-v1-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/recommendation-quality-daily")
  public Map<String, Object> recommendationQualityDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, outcome_type, attributed_count,
          unique_subjects, unique_books, p50_rank, calculation_version,
          refreshed_at, source_watermark
        FROM reporting.recommendation_quality_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, strategy, model_version, outcome_type
        """, days);
    return response("recommendation_quality_daily", "recommendation-outcome-v3-exact-viewable-touch-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/recommendation-position-daily")
  public Map<String, Object> recommendationPositionDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, rank_bucket, outcome_type,
          attributed_count, unique_subjects, unique_books,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.recommendation_position_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, strategy, model_version, rank_bucket, outcome_type
        """, days);
    return response("recommendation_position_daily", "recommendation-position-v3-exact-viewable-touch-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/recommendation-position-funnel-daily")
  public Map<String, Object> recommendationPositionFunnelDaily(
      @RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, model_version, strategy, rank_bucket, population, platform,
          impression_count, viewable_count, eligible_viewable_count,
          attributed_open_count, mature_attributed_open_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.recommendation_position_funnel_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, strategy, model_version, rank_bucket, platform
        """, days);
    return response("recommendation_position_funnel_daily",
        "recommendation-position-funnel-v1-mature-viewable-ctr-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/excerpt-performance")
  public Map<String, Object> excerptPerformance(
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "20") int minImpressions,
      @RequestParam(defaultValue = "200") int limit,
      @RequestParam(defaultValue = "0") int page,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    validatePage(page);
    if (minImpressions < 1 || minImpressions > 100000 || limit < 1 || limit > 500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "minImpressions must be 1..100000 and limit must be 1..500");
    }
    var rows = jdbc.queryForList("""
        SELECT day, excerpt_id, book_id, book_title, language, model_version, strategy,
          eligible, impressions, viewable_count, eligible_viewable_count,
          attributed_open_count, mature_attributed_open_count,
          attributed_read_count, attributed_complete_count, unique_subjects,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.excerpt_performance_daily
        WHERE day >= current_date - (? - 1)
          AND impressions >= ?
        ORDER BY day DESC, viewable_count DESC, attributed_open_count DESC, excerpt_id
        LIMIT ? OFFSET ?
        """, days, minImpressions, limit + 1, pageOffset(page, limit));
    return pagedResponse("excerpt_performance", "excerpt-performance-v3-mature-viewable-ctr-unknown-population", days,
        "SUPPRESSED_BELOW_MIN_SAMPLE", rows, page, limit);
  }

  @GetMapping("/experiment-daily")
  public Map<String, Object> experimentDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, experiment_id, assignment_version, variant, assigned_subjects,
          request_count, impression_count, viewable_count, eligible_viewable_count,
          attributed_open_count, mature_attributed_open_count,
          attributed_read_count, attributed_complete_count, unique_subjects,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.experiment_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, experiment_id, variant
        """, days);
    return response("experiment_daily", "feed-experiment-v3-mature-viewable-ctr-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/quality/access-audit")
  public Map<String, Object> accessAudit(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, action, event_count, unique_actors, unique_targets,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.access_audit_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, action
        """, days);
    return response("access_audit_daily", "access-audit-v1", days,
        "UNKNOWN", rows);
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
        "UNKNOWN", rows);
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
        "UNKNOWN", rows);
  }

  @GetMapping("/editorial-quality-daily")
  public Map<String, Object> editorialQualityDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, campaigns, sampled_items, pending_items, in_progress_items, completed_items,
          annotations, submitted_annotations, unique_annotators, disputed_items, adjudications,
          p50_elapsed_ms, p95_elapsed_ms, calculation_version, refreshed_at, source_watermark
        FROM reporting.editorial_quality_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("editorial_quality_daily", "editorial-quality-v1-unknown-population", days,
        "UNKNOWN", rows);
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
        "UNKNOWN", rows);
  }

  @GetMapping("/quality/events")
  public Map<String, Object> eventQuality(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, accepted_events, rejected_events, classified_events, duplicate_rejections,
          schema_rejections, payload_rejections, outbox_pending,
          outbox_published, outbox_failed_attempts,
          outbox_oldest_pending_age_seconds, calculation_version,
          refreshed_at, source_watermark
        FROM reporting.event_quality_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("event_quality_daily", "event-quality-classified-events-v2", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/quality/event-schema")
  public Map<String, Object> eventSchemaQuality(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, record_kind, event_type, schema_version, client_version,
          reject_reason, record_count, unknown_event_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.event_schema_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, record_kind, event_type, schema_version, client_version
        """, days);
    return response("event_schema_daily", "event-schema-v1-unknown-population", days,
        "UNKNOWN", rows);
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
        "UNKNOWN", rows);
  }

  @GetMapping("/reading-daily")
  public Map<String, Object> readingDaily(@RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, population, unique_readers, sessions, active_seconds,
          p50_session_seconds, p90_session_seconds,
          session_under_1m, session_1m_5m, session_5m_15m, session_15m_plus,
          books_opened,
          books_started, completions, saved_books, unique_savers,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.reading_daily
        WHERE day >= current_date - (? - 1) AND population = ?
        ORDER BY day
        """, days, validatePopulation(population));
    return response("reading_daily", "reading-session-duration-buckets-population-v4", days,
        population, rows);
  }

  @GetMapping("/reading-progress-daily")
  public Map<String, Object> readingProgressDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, reader_book_pairs_started, reader_book_pairs_10, reader_book_pairs_50,
          reader_book_pairs_100, save_actions, unsave_actions,
          unique_savers, unique_unsavers, calculation_version,
          refreshed_at, source_watermark
        FROM reporting.reading_progress_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("reading_progress_daily", "reading-progress-cohort-v2-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/library-state")
  public Map<String, Object> libraryState(Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    var rows = jdbc.queryForList("""
        SELECT saved_book_pairs, saved_books, saved_subject_keys,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.library_state_snapshot
        WHERE snapshot_key = 'CURRENT'
        """);
    var result = response("library_state_snapshot", "library-current-state-v1-unknown-population",
        1, "UNKNOWN", rows);
    result.put("unit", "current_snapshot");
    result.remove("from");
    result.remove("to");
    return result;
  }

  @GetMapping("/reading-habits-daily")
  public Map<String, Object> readingHabitsDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, active_readers, reading_minutes, completed_books,
          streak_1_day_users, streak_2_6_day_users,
          streak_7_29_day_users, streak_30_plus_day_users,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.reading_habits_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("reading_habits_daily", "reading-habits-v1-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/retention-cohort")
  public Map<String, Object> retentionCohort(@RequestParam(defaultValue = "366") int days,
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT cohort_day, offset_day, population, cohort_users, retained_users, mature,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.retention_cohort
        WHERE cohort_day >= current_date - (? - 1) AND population = ?
        ORDER BY cohort_day, offset_day
        """, days, validatePopulation(population));
    return response("retention_cohort", "retention-significant-read-v2-population", days,
        population, rows);
  }

  @GetMapping("/social-daily")
  public Map<String, Object> socialDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, follow_count, like_count, comment_count, share_count,
          book_opened_count, book_saved_count, unique_actors, reports_open,
          moderation_backlog, behavior_like_events, behavior_unlike_events,
          behavior_save_events, behavior_unsave_events, behavior_share_events,
          behavior_comment_events, behavior_unique_actors, behavior_distinct_books,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.social_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("social_daily", "product-activity-v1-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/moderation-daily")
  public Map<String, Object> moderationDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, open_count, in_review_count, resolved_count, dismissed_count,
          p50_resolution_seconds, p95_resolution_seconds, oldest_open_age_seconds,
          open_under_1_day, open_1_to_3_days, open_3_to_7_days, open_7_days_or_more,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.moderation_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("moderation_daily", "moderation-aging-buckets-v2-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/publisher-workflow-daily")
  public Map<String, Object> publisherWorkflowDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, submissions, approved_reviews, rejected_reviews, changes_requested,
          published_works, active_published_works, rights_attestations,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.publisher_workflow_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day
        """, days);
    return response("publisher_workflow_daily", "governance-activity-v1-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/editorial-submission-funnel-daily")
  public Map<String, Object> editorialSubmissionFunnelDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT cohort_day, submissions, reviewed_submissions, approved_submissions,
          published_submissions, active_published_submissions, rights_attested_submissions,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.editorial_submission_funnel_daily
        WHERE cohort_day >= current_date - (? - 1)
        ORDER BY cohort_day
        """, days);
    return response("editorial_submission_funnel_daily",
        "editorial-submission-funnel-v1-unknown-population", days, "UNKNOWN", rows);
  }

  @GetMapping("/rights-daily")
  public Map<String, Object> rightsDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, action, distribution_status, decision_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.rights_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, action, distribution_status
        """, days);
    return response("rights_daily", "governance-activity-v1-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/rights-status-daily")
  public Map<String, Object> rightsStatusDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, source_type, right_type, territory, status, item_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.rights_status_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, source_type, right_type, territory, status
        """, days);
    return response("rights_status_daily", "rights-status-v1-unknown-population", days,
        "UNKNOWN", rows);
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
    return response("publisher_daily", "product-activity-v1-unknown-population", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/book-engagement")
  public Map<String, Object> bookEngagement(@RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "5") int minReaders,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    validatePage(page);
    if (minReaders < 1 || minReaders > 10000 || limit < 1 || limit > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "minReaders must be 1..10000 and limit must be 1..200");
    }
    var rows = jdbc.queryForList("""
        SELECT day, population, aggregation_level, aggregation_key, book_id, book_title, language,
          impressions, viewable_count, opens, reads, active_minutes, unique_readers,
          completions, interactions, calculation_version, refreshed_at, source_watermark
        FROM reporting.book_engagement_daily
        WHERE day >= current_date - (? - 1)
          AND unique_readers >= ? AND population = ?
        ORDER BY day DESC, aggregation_level, unique_readers DESC, aggregation_key
        LIMIT ? OFFSET ?
        """, days, minReaders, validatePopulation(population), limit + 1, pageOffset(page, limit));
    return pagedResponse("book_engagement", "book-engagement-v2-population", days,
        population, rows, page, limit);
  }

  @GetMapping("/book-engagement-trends")
  public Map<String, Object> bookEngagementTrends(
      @RequestParam(defaultValue = "30") int days,
      @RequestParam(defaultValue = "5") int minReaders,
      @RequestParam(defaultValue = "5") int top,
      @RequestParam(defaultValue = "PRODUCT") String population,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    if (minReaders < 1 || minReaders > 10000 || top < 1 || top > 10) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "minReaders must be 1..10000 and top must be 1..10");
    }
    var rows = jdbc.queryForList("""
        WITH eligible AS (
          SELECT aggregation_level, aggregation_key,
              max(book_title) AS book_title, max(language) AS language,
              sum(active_minutes) AS period_active_minutes
          FROM reporting.book_engagement_daily
          WHERE day >= current_date - (? - 1) AND unique_readers >= ? AND population = ?
          GROUP BY aggregation_level, aggregation_key
        ), ranked AS (
          SELECT aggregation_level, aggregation_key, book_title, language,
              period_active_minutes,
              row_number() OVER (PARTITION BY aggregation_level
                ORDER BY period_active_minutes DESC, aggregation_key) AS entity_rank
          FROM eligible
        )
        SELECT d.day, d.aggregation_level, d.aggregation_key, d.book_title,
            d.language, d.active_minutes, d.unique_readers, d.completions,
            r.period_active_minutes, r.entity_rank, d.source_watermark,
            d.refreshed_at
        FROM reporting.book_engagement_daily d
        JOIN ranked r ON r.aggregation_level = d.aggregation_level
          AND r.aggregation_key = d.aggregation_key
        WHERE d.day >= current_date - (? - 1)
          AND d.unique_readers >= ? AND d.population = ? AND r.entity_rank <= ?
        ORDER BY d.aggregation_level, r.entity_rank, d.day
        """, days, minReaders, validatePopulation(population), days, minReaders, population, top);
    return response("book_engagement_trends",
        "book-engagement-trends-v2-population-min-sample", days,
        population, rows);
  }

  @GetMapping("/content-supply")
  public Map<String, Object> contentSupply(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, active_books, reader_ready_books, excerpt_ready_books,
          available_asset_versions,
          analyzed_asset_versions, eligible_excerpts, ranked_excerpts,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.content_supply_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at
        """, days);
    return response("content_supply_snapshot", "content-supply-v1", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/content-coverage")
  public Map<String, Object> contentCoverage(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, language, rights_status, active_books,
          books_with_active_assets, available_asset_books, reader_ready_books,
          analyzed_books, eligible_excerpt_books, ranked_books, eligible_excerpts,
          ranked_excerpts, calculation_version, refreshed_at, source_watermark
        FROM reporting.content_coverage_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at, language, rights_status
        """, days);
    return response("content_coverage_snapshot", "content-coverage-v1", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/content-rank-scores")
  public Map<String, Object> contentRankScores(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, language, rights_status, ranker_code, ranker_version,
          configuration_hash, score_decile, score_min, score_max, excerpt_count,
          calculation_version, refreshed_at, source_watermark
        FROM reporting.content_rank_score_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at, language, rights_status, ranker_code,
          ranker_version, configuration_hash, score_decile
        """, days);
    return response("content_rank_score_snapshot", "content-rank-score-deciles-v1",
        days, "UNKNOWN", rows);
  }

  @GetMapping("/excerpt-production")
  public Map<String, Object> excerptProduction(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, language, rights_status, generation_method, outcome,
          reason_bucket, excerpt_count, calculation_version, refreshed_at, source_watermark
        FROM reporting.excerpt_production_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at, language, rights_status, generation_method, outcome, reason_bucket
        """, days);
    return response("excerpt_production_snapshot", "excerpt-production-v1",
        days, "UNKNOWN", rows);
  }

  @GetMapping("/import-quality-daily")
  public Map<String, Object> importQualityDaily(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT day, source_code, item_status, error_bucket, rights_outcome,
          asset_outcome, item_count, retry_attempt_count, calculation_version,
          refreshed_at, source_watermark
        FROM reporting.import_quality_daily
        WHERE day >= current_date - (? - 1)
        ORDER BY day, source_code, item_status, error_bucket, rights_outcome, asset_outcome
        """, days);
    return response("import_quality_daily", "import-quality-v1", days,
        "UNKNOWN", rows);
  }

  @GetMapping("/backlog")
  public Map<String, Object> backlog(@RequestParam(defaultValue = "7") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, source_type, queue_name, pending_count,
          oldest_pending_age_seconds, failed_count, stuck_count, last_successful_at,
          CASE WHEN last_successful_at IS NULL THEN NULL
            ELSE greatest(0, extract(epoch FROM (now() - last_successful_at))::bigint) END
            AS freshness_age_seconds,
          source_watermark, record_count, refresh_status, calculation_version,
          refreshed_at
        FROM reporting.backlog_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at, source_type, queue_name
        """, days);
    return response("backlog_snapshot", "backlog-freshness-v1", days,
        "UNKNOWN", rows);
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
        "UNKNOWN", rows);
  }

  @GetMapping("/ingestion-queue")
  public Map<String, Object> ingestionQueue(@RequestParam(defaultValue = "30") int days,
      Principal principal, Authentication authentication) {
    requireAnalyst(authentication, principal);
    validateDays(days);
    var rows = jdbc.queryForList("""
        SELECT collected_at, queue_status, task_count, oldest_task_age_seconds,
          expired_lease_count, calculation_version, refreshed_at, source_watermark
        FROM reporting.ingestion_queue_snapshot
        WHERE collected_at >= now() - (? * INTERVAL '1 day')
        ORDER BY collected_at, queue_status
        """, days);
    return response("ingestion_queue_snapshot", "ingestion-queue-v1", days,
        "UNKNOWN", rows);
  }

  private Map<String, Object> response(String metricId, String definitionVersion, int days,
      String population, java.util.List<Map<String, Object>> rows) {
    var response = new LinkedHashMap<String, Object>();
    response.put("schemaVersion", "dashboard-response-v1");
    response.put("metricId", metricId);
    response.put("definitionVersion", definitionVersion);
    Instant sourceWatermark = latestInstant(rows, "source_watermark");
    Instant projectionUpdatedAt = latestInstant(rows, "refreshed_at");
    String projectionStatus = null;
    if (rows.isEmpty()) {
      var health = jdbc.queryForList("""
          SELECT last_successful_refresh AS refreshed_at, source_watermark, refresh_status
          FROM reporting.projection_health WHERE projection_name = ?
          """, metricId);
      if (!health.isEmpty()) {
        sourceWatermark = latestInstant(health, "source_watermark");
        projectionUpdatedAt = latestInstant(health, "refreshed_at");
        projectionStatus = String.valueOf(health.get(0).get("refresh_status"));
      }
    }
    long freshnessSeconds = projectionUpdatedAt == null
        ? 0
        : Math.max(0, Instant.now().getEpochSecond() - projectionUpdatedAt.getEpochSecond());
    boolean stale = projectionUpdatedAt != null && freshnessSeconds > staleAfterSeconds;
    String status = "ERROR".equalsIgnoreCase(projectionStatus) ? "ERROR"
        : projectionUpdatedAt == null ? "UNKNOWN_FRESHNESS"
        : stale ? "STALE" : rows.isEmpty() ? "NO_DATA" : "OK";
    response.put("status", status);
    response.put("unit", "aggregate");
    response.put("from", Instant.now().minusSeconds(days * 86400L).atOffset(ZoneOffset.UTC).toString());
    response.put("to", Instant.now().atOffset(ZoneOffset.UTC).toString());
    response.put("population", population);
    response.put("sourceWatermark", sourceWatermark == null ? null : sourceWatermark.toString());
    response.put("lastBusinessActivityAt", sourceWatermark == null ? null : sourceWatermark.toString());
    response.put("projectionUpdatedAt", projectionUpdatedAt == null ? null : projectionUpdatedAt.toString());
    response.put("refreshedAt", projectionUpdatedAt == null ? null : projectionUpdatedAt.toString());
    response.put("freshnessSeconds", projectionUpdatedAt == null ? null : freshnessSeconds);
    response.put("processingLagSeconds", sourceWatermark == null || projectionUpdatedAt == null
        ? null : Math.max(0, projectionUpdatedAt.getEpochSecond() - sourceWatermark.getEpochSecond()));
    var warnings = new ArrayList<String>();
    if (stale) warnings.add("projection refresh is older than the configured freshness threshold");
    if (!rows.isEmpty() && projectionUpdatedAt == null) warnings.add("projection refresh timestamp is unavailable");
    if (rows.isEmpty() && projectionUpdatedAt == null) warnings.add("projection refresh state is unavailable");
    response.put("warnings", warnings);
    response.put("series", rows);
    return response;
  }

  private Instant latestInstant(List<Map<String, Object>> rows, String key) {
    return rows.stream().map(row -> toInstant(row.get(key)))
        .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
  }

  private Map<String, Object> pagedResponse(String metricId, String definitionVersion, int days,
      String population, java.util.List<Map<String, Object>> rows, int page, int pageSize) {
    boolean hasNext = rows.size() > pageSize;
    var visibleRows = hasNext ? rows.subList(0, pageSize) : rows;
    var response = response(metricId, definitionVersion, days, population,
        new java.util.ArrayList<>(visibleRows));
    response.put("page", page);
    response.put("pageSize", pageSize);
    response.put("hasNext", hasNext);
    return response;
  }

  private void validateDays(int days) {
    if (days < 1 || days > 366) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 366");
    }
  }

  private void validatePage(int page) {
    if (page < 0 || page > 10000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be between 0 and 10000");
    }
  }

  private int pageOffset(int page, int pageSize) {
    long offset = (long) page * pageSize;
    if (offset > Integer.MAX_VALUE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page offset is too large");
    }
    return (int) offset;
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
