package com.bookrush.analytics.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnalyticsJobWorkerLeaseTest {
  @Test
  void staleLeaseRecoveryIsBoundedToActiveNonCancelledJobs() {
    var sql = AnalyticsJobWorker.staleLeaseRecoverySql();

    assertTrue(sql.contains("i.status='RUNNING'"));
    assertTrue(sql.contains("j.cancel_requested=false"));
    assertTrue(sql.contains("j.status IN ('PENDING','RUNNING')"));
    assertTrue(sql.contains("(? * INTERVAL '1 second')"));
    assertTrue(sql.contains("CASE WHEN i.attempt_count >= ? THEN 'FAILED' ELSE 'PENDING' END"));
    assertTrue(sql.contains("Lease expirado após atingir o limite de tentativas"));
  }

  @Test
  void staleLeaseThresholdCannotDisableRecovery() {
    assertEquals(1, AnalyticsJobWorker.normalizeStaleAfterSeconds(0));
    assertEquals(900, AnalyticsJobWorker.normalizeStaleAfterSeconds(900));
  }

  @Test
  void stalePhaseRecoveryRecordsAnAuditableFailureBeforeRequeue() {
    var sql = AnalyticsJobWorker.stalePhaseRecoverySql();

    assertTrue(sql.contains("p.status='RUNNING'"));
    assertTrue(sql.contains("status='FAILED'"));
    assertTrue(sql.contains("WORKER_RESTART_RECOVERY"));
    assertTrue(sql.contains("p.attempt_count=i.attempt_count"));
  }
}
