package com.bookrush.ingestion.jobs;

import com.bookrush.ingestion.retry.FailureClassifier;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Explicit dispatcher: manual jobs are awakened after commit even when periodic scheduling is disabled. */
@Service
public class IngestionDispatcher {
  private static final Logger log = LoggerFactory.getLogger(IngestionDispatcher.class);
  private final JdbcTemplate jdbc;
  private final LeaseCoordinator leases;
  private final GutenbergImportService gutenberg;
  private final Executor executor;

  public IngestionDispatcher(JdbcTemplate jdbc, LeaseCoordinator leases, GutenbergImportService gutenberg, Executor ingestionExecutor) {
    this.jdbc = jdbc; this.leases = leases; this.gutenberg = gutenberg; this.executor = ingestionExecutor;
  }

  public void dispatch(UUID jobId) {
    executor.execute(() -> run(jobId));
  }

  /** Re-awakens durable work after a process restart; no in-memory queue is authoritative. */
  @EventListener(ApplicationReadyEvent.class)
  public void recoverAfterRestart() {
    jdbc.query("SELECT id FROM catalog.ingestion_job WHERE status IN ('PENDING','RUNNING')", (rs, row) -> rs.getObject(1, UUID.class))
        .forEach(this::dispatch);
  }

  private void run(UUID jobId) {
    var parameters = jdbc.queryForObject("SELECT parameters::text FROM catalog.ingestion_job WHERE id=?", String.class, jobId);
    try {
      if (new com.fasterxml.jackson.databind.ObjectMapper().readTree(parameters).path("dryRun").asBoolean(false)) {
        jdbc.update("UPDATE catalog.ingestion_task SET status='SKIPPED', reason_code='DRY_RUN', started_at=COALESCE(started_at, clock_timestamp()), finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE ingestion_job_id=? AND status='PENDING'", jobId);
        jdbc.update("UPDATE catalog.ingestion_item SET status='SKIPPED', started_at=COALESCE(started_at, clock_timestamp()), finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE ingestion_job_id=? AND status='PENDING'", jobId);
        // A dry-run validates and skips work; it must not count skipped items as
        // succeeded/failed because the ingestion_job consistency check requires
        // items_processed = items_succeeded + items_failed.
        jdbc.update("UPDATE catalog.ingestion_job SET status='COMPLETED', started_at=COALESCE(started_at, clock_timestamp()), items_processed=0, items_succeeded=0, items_failed=0, finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE id=?", jobId);
        return;
      }
    } catch (Exception e) {
      jdbc.update("UPDATE catalog.ingestion_job SET status='FAILED', started_at=COALESCE(started_at, clock_timestamp()), error_message=?, finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE id=?", "invalid dry-run parameters: " + e.getMessage(), jobId);
      return;
    }
    jdbc.update("UPDATE catalog.ingestion_job SET status='RUNNING', started_at=COALESCE(started_at, clock_timestamp()), updated_at=clock_timestamp() WHERE id=? AND status='PENDING'", jobId);
    while (true) {
      var lease = leases.claim("ingestion-" + UUID.randomUUID(), Duration.ofMinutes(2), jobId);
      if (lease.isEmpty()) break;
      try {
        gutenberg.process(lease.get());
        leases.complete(lease.get(), "SUCCEEDED", "IMPORTED");
        updateCounters(jobId);
      } catch (Exception failure) {
        log.warn("Ingestion task {} failed: {}", lease.get().taskId(), failureDetail(failure));
        var failureClass = FailureClassifier.exception(failure);
        boolean retry = false;
        try { retry = leases.retry(lease.get(), failureClass, String.valueOf(failure.getMessage())); } catch (RuntimeException ignored) { }
        if (retry) {
          jdbc.update("UPDATE catalog.ingestion_item SET status='PENDING', error_code=?, error_message=?, updated_at=clock_timestamp() WHERE id=(SELECT ingestion_item_id FROM catalog.ingestion_task WHERE id=?)", failureClass.name(), String.valueOf(failure.getMessage()), lease.get().taskId());
          continue;
        }
        try { leases.complete(lease.get(), "FAILED", failureClass.name()); } catch (RuntimeException ignored) { }
        String detail = failureDetail(failure);
        jdbc.update("UPDATE catalog.ingestion_item SET status='FAILED', error_code=?, error_message=?, started_at=COALESCE(started_at, clock_timestamp()), finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE id=(SELECT ingestion_item_id FROM catalog.ingestion_task WHERE id=?)", failure.getClass().getSimpleName(), detail, lease.get().taskId());
        updateCounters(jobId);
      }
    }
    jdbc.update("UPDATE catalog.ingestion_job SET status=CASE WHEN EXISTS (SELECT 1 FROM catalog.ingestion_task WHERE ingestion_job_id=? AND status='RETRY_WAIT') THEN 'PAUSED' WHEN items_failed=0 THEN 'COMPLETED' ELSE 'COMPLETED_WITH_ERRORS' END, finished_at=CASE WHEN EXISTS (SELECT 1 FROM catalog.ingestion_task WHERE ingestion_job_id=? AND status='RETRY_WAIT') THEN NULL ELSE clock_timestamp() END, updated_at=clock_timestamp() WHERE id=? AND status='RUNNING'", jobId, jobId, jobId);
  }

  /** Counters describe logical items, never retry attempts. */
  private void updateCounters(UUID jobId) {
    jdbc.update("""
      UPDATE catalog.ingestion_job j SET
        items_processed=(SELECT count(*) FROM catalog.ingestion_item i WHERE i.ingestion_job_id=j.id AND i.status IN ('SUCCEEDED','FAILED','CANCELLED')),
        items_succeeded=(SELECT count(*) FROM catalog.ingestion_item i WHERE i.ingestion_job_id=j.id AND i.status='SUCCEEDED'),
        items_failed=(SELECT count(*) FROM catalog.ingestion_item i WHERE i.ingestion_job_id=j.id AND i.status='FAILED'),
        updated_at=clock_timestamp() WHERE j.id=?
      """, jobId);
  }

  private static String failureDetail(Throwable failure) {
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      Throwable root = failure;
      while (root.getCause() != null && root.getCause() != root) root = root.getCause();
      message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
    return (failure.getClass().getSimpleName() + ": " + message).substring(0, Math.min(1000, failure.getClass().getSimpleName().length() + 2 + message.length()));
  }
}

@Configuration
class IngestionExecutorConfiguration {
  @Bean
  Executor ingestionExecutor() { return java.util.concurrent.Executors.newCachedThreadPool(); }
}
