package com.bookrush.ingestion.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class IngestionMetrics {
  private final io.micrometer.core.instrument.Counter processed;
  private final io.micrometer.core.instrument.Counter failed;
  private final io.micrometer.core.instrument.Counter discovered;
  private final io.micrometer.core.instrument.Counter acquired;
  private final io.micrometer.core.instrument.Counter normalized;
  private final io.micrometer.core.instrument.Counter retried;
  private final io.micrometer.core.instrument.Counter attemptFailed;

  public IngestionMetrics(MeterRegistry registry) {
    processed = registry.counter("bookrush.ingestion.items", "result", "processed");
    failed = registry.counter("bookrush.ingestion.items", "result", "failed");
    discovered = registry.counter("bookrush.ingestion.items", "result", "discovered");
    acquired = registry.counter("bookrush.ingestion.items", "result", "acquired");
    normalized = registry.counter("bookrush.ingestion.items", "result", "normalized");
    retried = registry.counter("bookrush.ingestion.items", "result", "retry");
    attemptFailed = registry.counter("bookrush.ingestion.items", "result", "attempt_failed");
  }

  public void processed() {
    processed.increment();
  }

  public void failed() {
    failed.increment();
  }

  public void discovered(long count) {
    if (count > 0) discovered.increment(count);
  }

  public void acquired() {
    acquired.increment();
  }

  public void normalized() {
    normalized.increment();
  }

  public void retried() {
    retried.increment();
  }

  public void attemptFailed() {
    attemptFailed.increment();
  }
}
