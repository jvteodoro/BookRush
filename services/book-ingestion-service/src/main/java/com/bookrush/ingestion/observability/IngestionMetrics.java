package com.bookrush.ingestion.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class IngestionMetrics {
  private final io.micrometer.core.instrument.Counter processed;
  private final io.micrometer.core.instrument.Counter failed;
  public IngestionMetrics(MeterRegistry registry) {
    processed = registry.counter("bookrush.ingestion.items", "result", "processed");
    failed = registry.counter("bookrush.ingestion.items", "result", "failed");
  }
  public void processed() { processed.increment(); }
  public void failed() { failed.increment(); }
}
