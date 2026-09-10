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

/** Explicit dispatcher: manual jobs are awakened after commit even when periodic scheduling is disabled. */
@Service
public class IngestionDispatcher {
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
        jdbc.update("UPDATE catalog.ingestion_job SET items_processed=items_processed+1, items_succeeded=items_succeeded+1, updated_at=clock_timestamp() WHERE id=?", jobId);
      } catch (Exception failure) {
        var failureClass = FailureClassifier.exception(failure);
        boolean retry = false;
        try { retry = leases.retry(lease.get(), failureClass, String.valueOf(failure.getMessage())); } catch (RuntimeException ignored) { }
        if (retry) {
          jdbc.update("UPDATE catalog.ingestion_item SET status='PENDING', error_code=?, error_message=?, updated_at=clock_timestamp() WHERE id=(SELECT ingestion_item_id FROM catalog.ingestion_task WHERE id=?)", failureClass.name(), String.valueOf(failure.getMessage()), lease.get().taskId());
          continue;
        }
        try { leases.complete(lease.get(), "FAILED", failureClass.name()); } catch (RuntimeException ignored) { }
        jdbc.update("UPDATE catalog.ingestion_item SET status='FAILED', error_code=?, error_message=?, started_at=COALESCE(started_at, clock_timestamp()), finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE id=(SELECT ingestion_item_id FROM catalog.ingestion_task WHERE id=?)", failure.getClass().getSimpleName(), String.valueOf(failure.getMessage()), lease.get().taskId());
        jdbc.update("UPDATE catalog.ingestion_job SET items_processed=items_processed+1, items_failed=items_failed+1, updated_at=clock_timestamp() WHERE id=?", jobId);
      }
    }
    jdbc.update("UPDATE catalog.ingestion_job SET status=CASE WHEN EXISTS (SELECT 1 FROM catalog.ingestion_task WHERE ingestion_job_id=? AND status='RETRY_WAIT') THEN 'PAUSED' WHEN items_failed=0 THEN 'COMPLETED' ELSE 'COMPLETED_WITH_ERRORS' END, finished_at=CASE WHEN EXISTS (SELECT 1 FROM catalog.ingestion_task WHERE ingestion_job_id=? AND status='RETRY_WAIT') THEN NULL ELSE clock_timestamp() END, updated_at=clock_timestamp() WHERE id=? AND status='RUNNING'", jobId, jobId, jobId);
  }
}

@Configuration
class IngestionExecutorConfiguration {
  @Bean
  Executor ingestionExecutor() { return java.util.concurrent.Executors.newCachedThreadPool(); }
}
