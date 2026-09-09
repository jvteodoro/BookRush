package com.bookrush.ingestion.jobs;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Finds durable work left by interrupted workers; repairs are explicit and idempotent. */
@Service
public class ReconciliationJob {
  private final JdbcTemplate jdbc;
  public ReconciliationJob(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public List<Map<String,Object>> staleTasks() {
    return jdbc.queryForList("SELECT id, status, operation_key, fence_token FROM catalog.ingestion_task WHERE status='RUNNING' AND lease_until < clock_timestamp() ORDER BY updated_at");
  }
  public int requeueExpired() {
    return jdbc.update("UPDATE catalog.ingestion_task SET status='RETRY_WAIT', claimed_by=NULL, lease_until=NULL, next_attempt_at=clock_timestamp(), updated_at=clock_timestamp() WHERE status='RUNNING' AND lease_until < clock_timestamp()");
  }
}
