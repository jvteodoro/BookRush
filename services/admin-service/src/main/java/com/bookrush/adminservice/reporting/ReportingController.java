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
