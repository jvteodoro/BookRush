UPDATE analytics.analysis_phase_timing p
   SET status='FAILED',
       finished_at=COALESCE(p.finished_at, clock_timestamp()),
       duration_ms=GREATEST(0, (EXTRACT(EPOCH FROM (COALESCE(p.finished_at, clock_timestamp()) - p.started_at)) * 1000)::bigint),
       error_code='WORKER_RESTART_RECOVERY'
  FROM analytics.analysis_job_item i
 WHERE p.job_item_id=i.id
   AND p.job_id=i.job_id
   AND p.status='RUNNING'
   AND p.attempt_count < i.attempt_count;
