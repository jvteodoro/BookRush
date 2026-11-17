package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.ingestion.observability.IngestionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class IngestionMetricsTest {
  @Test
  void recordsOnlyBoundedStageAndOutcomeLabels() {
    var registry = new SimpleMeterRegistry();
    var metrics = new IngestionMetrics(registry);

    metrics.discovered(4);
    metrics.acquired();
    metrics.normalized();
    metrics.processed();
    metrics.failed();
    metrics.retried();
    metrics.attemptFailed();

    assertEquals(4.0, registry.counter("bookrush.ingestion.items", "result", "discovered").count());
    assertEquals(1.0, registry.counter("bookrush.ingestion.items", "result", "acquired").count());
    assertEquals(1.0, registry.counter("bookrush.ingestion.items", "result", "normalized").count());
    assertEquals(1.0, registry.counter("bookrush.ingestion.items", "result", "processed").count());
    assertEquals(1.0, registry.counter("bookrush.ingestion.items", "result", "failed").count());
    assertEquals(1.0, registry.counter("bookrush.ingestion.items", "result", "retry").count());
    assertEquals(1.0, registry.counter("bookrush.ingestion.items", "result", "attempt_failed").count());
  }
}
