package com.bookrush.behaviorservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DataQualityReconciliationTest {
  @Test
  void reportsHealthyProjectionAndRecordsLowCardinalityMetric() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(10L, 4L, 3L, 0L);
    when(jdbc.queryForObject(anyString(), eq(Instant.class)))
        .thenReturn(Instant.parse("2026-10-20T00:00:00Z"), Instant.parse("2026-10-19T23:59:59Z"));
    var registry = new SimpleMeterRegistry();
    var reconciliation = new DataQualityReconciliation(jdbc, registry);

    var report = reconciliation.reconcile();

    assertTrue(report.healthy());
    assertEquals(10, report.events());
    assertEquals(1, report.projectionAgeSeconds());
    assertEquals(1, registry.counter("bookrush.behavior.data_quality.reconciliation",
        "outcome", "healthy").count());
  }

  @Test
  void preservesOrphanedAttributionAsAnIssue() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(10L, 4L, 3L, 2L);
    when(jdbc.queryForObject(anyString(), eq(Instant.class))).thenReturn(null, null);
    var reconciliation = new DataQualityReconciliation(jdbc, new SimpleMeterRegistry());

    var report = reconciliation.reconcile();

    assertEquals(2, report.orphaned());
    assertTrue(!report.healthy());
  }
}
