-- Optional narrative/emotion NLI observations. Raw labels and derived scores
-- are separate measurements so model scores are never mistaken for probabilities.
INSERT INTO analytics.analyzer(id, code, analyzer_type, implementation, code_version, model_name, model_version)
VALUES ('70000000-0000-4000-8000-000000000011','mdeberta-nli-v1','CLASSIFIER',
        'book-analytics-runtime','1','MoritzLaurer/mDeBERTa-v3-base-mnli-xnli','8adb042b4e0e4e4a8d5d9b8e8f9f2e6f4f1f7d72')
ON CONFLICT (code) DO NOTHING;

WITH concepts(kind, code) AS (
  SELECT 'narrative', unnest(ARRAY['conflict','suspense','introspection','self_containment','curiosity_gap','cliffhanger','revelation','resolution','quotability'])
  UNION ALL
  SELECT 'emotion', unnest(ARRAY['joy','sadness','fear','anger','surprise','disgust','affection'])
), metrics(code) AS (
  SELECT unnest(ARRAY['entailment','neutral','contradiction','support','confidence'])
)
INSERT INTO analytics.feature_definition
  (id, code, name, description, scope, value_type, unit, version, semantic_category, language_codes, algorithm_version)
SELECT md5('analytics-v2-nli:' || kind || ':' || concepts.code || ':' || metrics.code)::uuid,
       kind || '.' || concepts.code || '.' || metrics.code,
       initcap(replace(kind || ' ' || concepts.code || ' ' || metrics.code, '_', ' ')),
       'Raw NLI label or derived model score for the versioned ' || kind || ' hypothesis.',
       'EXCERPT', 'NUMBER', CASE WHEN metrics.code IN ('support','confidence') THEN 'model_score' ELSE 'probability' END,
       1, 'MODEL_INFERENCE', ARRAY['en','pt'], 'mdeberta-nli-v1'
FROM concepts CROSS JOIN metrics
ON CONFLICT (code) DO NOTHING;
