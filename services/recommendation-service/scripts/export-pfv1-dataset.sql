-- PFV1 training handoff, schema recommendation-pfv1-dataset-v1.
-- Read-only export. Run against a snapshot and provide :cutoff as a timestamptz.
-- The query intentionally excludes rows whose outcome happened after the cutoff.
\set ON_ERROR_STOP on

\copy (
  SELECT
    'recommendation-pfv1-dataset-v1' AS schema_version,
    r.id AS request_id,
    i.id AS recommendation_item_id,
    i.book_id,
    r.subject_key,
    r.created_at AS decision_at,
    i.rank,
    i.score,
    i.reason AS score_components,
    r.model_version,
    r.strategy,
    r.candidate_set_hash,
    COALESCE((r.context ->> 'personalizationPath'), 'FALLBACK') AS personalization_path,
    COALESCE((r.context ->> 'personalizationReason'), 'unknown') AS personalization_reason,
    imp.id AS impression_id,
    imp.shown_at,
    imp.viewable,
    CASE WHEN imp.viewable THEN 1 ELSE 0 END AS viewed_label,
    :cutoff::timestamptz AS dataset_cutoff
  FROM recommendation.request r
  JOIN recommendation.item i ON i.request_id = r.id
  LEFT JOIN recommendation.impression imp ON imp.item_id = i.id
  WHERE r.created_at < :cutoff::timestamptz
    AND (imp.shown_at IS NULL OR imp.shown_at < :cutoff::timestamptz)
  ORDER BY r.created_at, r.id, i.rank
) TO STDOUT WITH CSV HEADER;
