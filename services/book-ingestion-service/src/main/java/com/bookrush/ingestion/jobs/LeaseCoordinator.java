package com.bookrush.ingestion.jobs;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Single claim authority for ingestion_task. No network or object-storage I/O occurs here. */
@Service
public final class LeaseCoordinator {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  @Autowired
  public LeaseCoordinator(JdbcTemplate jdbc) {
    this(jdbc, Clock.systemUTC());
  }

  LeaseCoordinator(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public Optional<Lease> claim(String workerId, Duration leaseDuration) {
    if (workerId == null || workerId.isBlank() || leaseDuration.isNegative() || leaseDuration.isZero()) {
      throw new IllegalArgumentException("worker and positive lease are required");
    }
    var rows = jdbc.query("""
        WITH candidate AS (
          SELECT id FROM catalog.ingestion_task
          WHERE status IN ('PENDING','RETRY_WAIT')
            AND next_attempt_at <= clock_timestamp()
            AND (lease_until IS NULL OR lease_until <= clock_timestamp())
          ORDER BY next_attempt_at, created_at, id
          FOR UPDATE SKIP LOCKED LIMIT 1
        )
        UPDATE catalog.ingestion_task t
        SET status='RUNNING', claimed_by=?, lease_until=clock_timestamp() + (? * interval '1 second'),
            heartbeat_at=clock_timestamp(), fence_token=t.fence_token+1,
            attempt_count=t.attempt_count+1, started_at=COALESCE(t.started_at, clock_timestamp()),
            updated_at=clock_timestamp()
        FROM candidate c WHERE t.id=c.id
        RETURNING t.id, t.fence_token, t.lease_until
        """, (rs, rowNum) -> new Lease(rs.getObject("id", UUID.class), rs.getLong("fence_token"), rs.getTimestamp("lease_until").toInstant(), workerId),
        workerId, leaseDuration.toSeconds());
    return rows.stream().findFirst();
  }

  public void heartbeat(Lease lease, Duration leaseDuration) {
    var count = jdbc.update("""
        UPDATE catalog.ingestion_task
        SET lease_until=clock_timestamp() + (? * interval '1 second'), heartbeat_at=clock_timestamp(), updated_at=clock_timestamp()
        WHERE id=? AND status='RUNNING' AND claimed_by=? AND fence_token=? AND lease_until > clock_timestamp()
        """, leaseDuration.toSeconds(), lease.taskId(), lease.workerId(), lease.fenceToken());
    if (count != 1) throw new StaleLeaseException(lease.taskId());
  }

  public void complete(Lease lease, String status, String reason) {
    if (!java.util.Set.of("SUCCEEDED", "NOOP", "SKIPPED", "QUARANTINED", "FAILED", "CANCELLED").contains(status)) {
      throw new IllegalArgumentException("invalid terminal task status");
    }
    var count = jdbc.update("""
        UPDATE catalog.ingestion_task
        SET status=?, reason_code=?, finished_at=clock_timestamp(), lease_until=NULL, updated_at=clock_timestamp()
        WHERE id=? AND status='RUNNING' AND claimed_by=? AND fence_token=? AND lease_until > clock_timestamp()
        """, status, reason, lease.taskId(), lease.workerId(), lease.fenceToken());
    if (count != 1) throw new StaleLeaseException(lease.taskId());
  }

  public record Lease(UUID taskId, long fenceToken, java.time.Instant leaseUntil, String workerId) {
    public Lease(UUID taskId, long fenceToken, java.time.Instant leaseUntil) {
      this(taskId, fenceToken, leaseUntil, "");
    }
  }

  public static final class StaleLeaseException extends RuntimeException {
    public StaleLeaseException(UUID taskId) { super("lease is stale or no longer owned: " + taskId); }
  }
}
