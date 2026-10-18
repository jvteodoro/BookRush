CREATE TEMP TABLE normalized_phase_attempts ON COMMIT DROP AS
WITH item_starts AS (
  SELECT id,
         job_item_id,
         started_at,
         row_number() OVER (PARTITION BY job_item_id ORDER BY started_at, id)::INTEGER AS normalized_attempt
    FROM analytics.analysis_phase_timing
   WHERE phase = 'ITEM_TOTAL'
), mapped AS (
  SELECT p.id,
         COALESCE((
           SELECT s.normalized_attempt
             FROM item_starts s
            WHERE s.job_item_id = p.job_item_id
              AND (s.started_at < p.started_at OR (s.started_at = p.started_at AND s.id <= p.id))
            ORDER BY s.started_at DESC, s.id DESC
            LIMIT 1
         ), p.attempt_count) AS normalized_attempt
    FROM analytics.analysis_phase_timing p
)
SELECT id, normalized_attempt FROM mapped;

UPDATE analytics.analysis_phase_timing p
   SET attempt_count = n.normalized_attempt
  FROM normalized_phase_attempts n
 WHERE p.id = n.id
   AND p.attempt_count <> n.normalized_attempt;

UPDATE analytics.analysis_phase_log l
   SET attempt_count = p.attempt_count
  FROM analytics.analysis_phase_timing p
 WHERE p.id = l.phase_timing_id
   AND l.attempt_count <> p.attempt_count;

UPDATE analytics.analysis_job_item i
   SET attempt_count = history.max_attempt
  FROM (
    SELECT job_item_id, MAX(attempt_count) AS max_attempt
      FROM analytics.analysis_phase_timing
     GROUP BY job_item_id
  ) history
 WHERE i.id = history.job_item_id
   AND i.attempt_count < history.max_attempt;
