package com.bookrush.ingestion.jobs;

import java.time.Duration;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CleanupJob {
  private final JdbcTemplate jdbc;
  public CleanupJob(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public int expireLeases(Duration olderThan) {
    Objects.requireNonNull(olderThan);
    return jdbc.update("UPDATE catalog.ingestion_task SET status='RETRY_WAIT', claimed_by=NULL, lease_until=NULL, next_attempt_at=clock_timestamp(), updated_at=clock_timestamp() WHERE status='RUNNING' AND heartbeat_at < clock_timestamp() - (? * interval '1 second')", olderThan.toSeconds());
  }
}
