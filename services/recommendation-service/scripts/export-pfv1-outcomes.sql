\set ON_ERROR_STOP on
\copy (
  SELECT
    oa.recommendation_item_id,
    oa.outcome_type,
    oa.occurred_at,
    e.payload->>'percent' AS percent,
    e.payload->>'activeSeconds' AS active_seconds
  FROM behavior.outcome_attribution oa
  JOIN behavior.events e ON e.id = oa.source_event_id
  WHERE oa.recommendation_item_id IS NOT NULL
    AND oa.occurred_at < :cutoff::timestamptz
  ORDER BY oa.recommendation_item_id, oa.occurred_at
) TO STDOUT WITH CSV HEADER;
