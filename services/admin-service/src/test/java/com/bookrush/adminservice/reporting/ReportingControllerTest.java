package com.bookrush.adminservice.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        () -> controller.readerDay(30, principal, reader)).getStatusCode().value());
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.readerDay(367, principal, analyst)).getStatusCode().value());
  }

  @Test
  void reportsUntrustedPopulationInsteadOfPretendingProductData() {
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "population", "UNCLASSIFIED", "platform", "UNKNOWN",
        "refreshed_at", Timestamp.from(Instant.parse("2026-11-04T12:00:00Z")),
        "source_watermark", Timestamp.from(Instant.parse("2026-11-04T11:59:00Z")),
        "active_users", 2L)));
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");

    var response = controller.readerDay(7, principal, analyst);

    assertEquals("UNTRUSTED", response.get("status"));
    assertEquals("UNCLASSIFIED", response.get("population"));
  }

  @Test
  void exposesFeedAndPipelineSnapshotsOnlyToReportingRoles() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "strategy", "PERSONALIZED", "served_count", 4L,
        "viewable_count", 3L, "attributed_open_count", 1L,
        "source_watermark", Timestamp.from(Instant.parse("2026-11-04T11:59:00Z")))))
        .thenReturn(List.of(Map.of(
            "collected_at", Timestamp.from(Instant.parse("2026-11-04T12:00:00Z")),
            "phase", "ITEM", "job_status", "COMPLETED", "status_count", 4L,
            "failure_count", 0L, "throughput", 2L,
            "source_watermark", Timestamp.from(Instant.parse("2026-11-04T11:59:00Z")))));

    assertEquals("UNTRUSTED", controller.feedDaily(7, principal, analyst).get("status"));
    assertEquals("UNTRUSTED", controller.pipeline(7, principal, analyst).get("status"));
    assertEquals("UNTRUSTED", controller.attributionQuality(7, principal, analyst).get("status"));
    assertEquals("UNTRUSTED", controller.reconciliationQuality(7, principal, analyst).get("status"));

    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.feedDaily(7, principal, reader)).getStatusCode().value());
  }

  @Test
  void comparesEqualExecutivePeriodsWithoutInferringMissingDataAsZero() {
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    Map<String, Object> reader = Map.of("has_data", true, "daily_peak_users", 4L,
        "sessions", 8L, "active_seconds", 600L, "meaningful_reads", 3L);
    Map<String, Object> feed = Map.of("has_data", true, "served", 10L, "viewable", 8L,
        "opened", 4L, "meaningful", 2L, "complete", 1L);
    when(jdbc.queryForMap(anyString(), any(Object[].class)))
        .thenReturn(reader, feed, reader, feed);

    var response = controller.executiveSummary(7, principal, analyst);

    assertEquals("UNTRUSTED", response.get("status"));
    assertEquals("current", ((Map<?, ?>) response.get("current")).get("label"));
    assertEquals("previous", ((Map<?, ?>) response.get("previous")).get("label"));
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
        "source_watermark", Timestamp.from(Instant.parse("2026-11-04T11:59:00Z")));
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(row));

    var response = controller.editorialQualityDaily(7, principal, analyst);

    assertEquals("UNTRUSTED", response.get("status"));
    assertEquals("UNCLASSIFIED", response.get("population"));
    assertEquals(1, ((List<?>) response.get("series")).size());
  }
}
