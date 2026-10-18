package com.bookrush.analytics.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnalyticsJobWorkerLeaseTest {
  @Test
  void staleLeaseRecoveryIsBoundedToActiveNonCancelledJobs() {
    var sql = AnalyticsJobWorker.staleLeaseRecoverySql();

    assertEquals(7, sql.chars().filter(character -> character == '?').count());
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

  @Test
  void exhaustedPendingRecoveryCannotLeaveAnUnclaimableItemInQueue() {
    var sql = AnalyticsJobWorker.exhaustedPendingRecoverySql();

    assertTrue(sql.contains("i.status='PENDING'"));
    assertTrue(sql.contains("i.attempt_count >= ?"));
    assertTrue(sql.contains("status='FAILED'"));
    assertTrue(sql.contains("WORKER_RESTART_RECOVERY"));
  }

  @Test
  void documentFeatureInsertBindsEveryPlaceholder() {
    var sql = AnalyticsJobWorker.documentFeatureInsertSql();

    assertEquals(4, sql.chars().filter(character -> character == '?').count());
    assertTrue(sql.contains("numeric_value,value_status,computed_at"));
    assertTrue(sql.contains("SELECT ?,fd.id,ar.input_asset_version_id,?, 'VALID'"));
  }
}
