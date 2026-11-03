-- Explainable spoiler signals. These are components, never a calibrated probability.
INSERT INTO analytics.feature_definition
  (id, code, name, description, scope, value_type, unit, min_value, max_value, version,
   semantic_category, language_codes, algorithm_version)
VALUES
  ('77000000-0000-4000-8000-000000000001', 'spoiler.position', 'Spoiler position component',
   'Normalized book-relative position used as an explainable spoiler component.', 'EXCERPT', 'NUMBER', 'ratio', 0, 1, 1,
   'MODEL_INFERENCE', ARRAY['all'], 'spoiler-components-v1'),
  ('77000000-0000-4000-8000-000000000002', 'spoiler.resolution', 'Spoiler resolution component',
   'NLI resolution support used as an explainable spoiler component.', 'EXCERPT', 'NUMBER', 'model_score', 0, 1, 1,
   'MODEL_INFERENCE', ARRAY['en','pt'], 'spoiler-components-v1'),
  ('77000000-0000-4000-8000-000000000003', 'spoiler.revelation', 'Spoiler revelation component',
   'NLI revelation support used as an explainable spoiler component.', 'EXCERPT', 'NUMBER', 'model_score', 0, 1, 1,
   'MODEL_INFERENCE', ARRAY['en','pt'], 'spoiler-components-v1')
ON CONFLICT (code) DO NOTHING;
