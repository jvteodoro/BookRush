WITH concepts(code) AS (
  SELECT unnest(ARRAY['action','dialogue','description','reflection','conflict','mystery'])
)
INSERT INTO analytics.feature_definition
  (id, code, name, description, scope, value_type, unit, version, semantic_category, language_codes, algorithm_version)
SELECT md5('analytics-v2-prototype:' || code)::uuid,
       'semantic.prototype.' || code || '.raw_cosine',
       initcap(replace(code, '_', ' ')) || ' prototype similarity',
       'Raw cosine similarity to the versioned semantic prototype centroid; not a probability.',
       'EXCERPT', 'NUMBER', 'cosine', 2, 'MODEL_INFERENCE', ARRAY['en','pt'], 'semantic-prototype-set-v2.0.0'
FROM concepts
ON CONFLICT (code) DO NOTHING;
