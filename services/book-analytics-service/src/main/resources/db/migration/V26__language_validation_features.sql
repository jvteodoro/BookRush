-- Persist language validation evidence at document scope without changing prior migrations.
INSERT INTO analytics.feature_definition
  (id, code, name, description, scope, value_type, unit, version)
VALUES
  ('71000000-0000-4000-8000-000000000101', 'language.detected',
   'Detected language', 'Language returned by the local fastText adapter',
   'DOCUMENT', 'TEXT', 'iso-639-1', 1),
  ('71000000-0000-4000-8000-000000000102', 'language.confidence',
   'Language confidence', 'Confidence returned by the local fastText adapter',
   'DOCUMENT', 'NUMBER', 'ratio', 1),
  ('71000000-0000-4000-8000-000000000103', 'language.agreement',
   'Language agreement', 'Agreement between detected and catalog language',
   'DOCUMENT', 'NUMBER', 'boolean', 1)
ON CONFLICT (code) DO NOTHING;
