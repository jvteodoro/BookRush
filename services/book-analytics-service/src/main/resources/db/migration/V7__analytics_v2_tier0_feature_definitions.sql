-- Content Analytics V2: feature-definition metadata and Tier 0 observations.
-- This migration is additive; V1 through V6 remain immutable.
ALTER TABLE analytics.feature_definition
  ADD COLUMN semantic_category VARCHAR(32) NOT NULL DEFAULT 'MEASUREMENT'
    CHECK (semantic_category IN ('MEASUREMENT', 'MODEL_INFERENCE', 'PRODUCT_SCORE')),
  ADD COLUMN language_codes VARCHAR(8)[] NOT NULL DEFAULT ARRAY['all'],
  ADD COLUMN algorithm_version VARCHAR(120) NOT NULL DEFAULT 'v1';

INSERT INTO analytics.analyzer(id, code, analyzer_type, implementation, code_version)
VALUES ('70000000-0000-4000-8000-000000000007', 'deterministic-text-v2', 'DETERMINISTIC',
        'com.bookrush.analytics.features.TextFeatureCalculator', '2')
ON CONFLICT (code) DO NOTHING;

-- Existing V1 codes retain their historical formula/version. V2 adds only
-- definitions that did not exist, avoiding reinterpretation of old results.
INSERT INTO analytics.feature_definition
  (id, code, name, description, scope, value_type, unit, min_value, max_value, version,
   semantic_category, language_codes, algorithm_version)
VALUES
  ('73000000-0000-4000-8000-000000000001', 'paragraph_count', 'Paragraph count',
   'Non-empty paragraph blocks in the excerpt.', 'EXCERPT', 'NUMBER', 'paragraphs', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000002', 'avg_sentence_length', 'Average sentence length',
   'Unicode token count divided by detected sentence count.', 'EXCERPT', 'NUMBER', 'words_per_sentence', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000003', 'sentence_length_std', 'Sentence length standard deviation',
   'Population standard deviation of Unicode token counts per sentence.', 'EXCERPT', 'NUMBER', 'words', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000004', 'sentence_length_cv', 'Sentence length coefficient of variation',
   'Population standard deviation divided by mean sentence token count; zero when all sentence lengths are zero.', 'EXCERPT', 'NUMBER', 'ratio', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000005', 'sentence_length_delta_mean', 'Mean adjacent sentence-length delta',
   'Mean absolute difference of adjacent sentence token counts.', 'EXCERPT', 'NUMBER', 'words', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000006', 'short_sentence_ratio', 'Short sentence ratio',
   'Fraction of sentences at or below the analyzer short-sentence threshold.', 'EXCERPT', 'NUMBER', 'ratio', 0, 1, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000007', 'short_sentence_burst', 'Short sentence burst',
   'Longest contiguous run of sentences at or below the analyzer short-sentence threshold.', 'EXCERPT', 'NUMBER', 'sentences', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000008', 'paragraph_length_cv', 'Paragraph length coefficient of variation',
   'Population standard deviation divided by mean Unicode token count per non-empty paragraph.', 'EXCERPT', 'NUMBER', 'ratio', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000009', 'exclamation_ratio', 'Exclamation ratio',
   'Exclamation marks divided by detected sentence count.', 'EXCERPT', 'NUMBER', 'ratio', 0, 1, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000010', 'punctuation_density', 'Punctuation density',
   'Recognized punctuation code points divided by all Unicode code points.', 'EXCERPT', 'NUMBER', 'ratio', 0, 1, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000011', 'ellipsis_density', 'Ellipsis density',
   'Ellipsis marks divided by detected sentence count.', 'EXCERPT', 'NUMBER', 'ratio', 0, NULL, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000012', 'dash_density', 'Dash density',
   'Em dash and en dash code points divided by detected sentence count.', 'EXCERPT', 'NUMBER', 'ratio', 0, NULL, 1,
   'MEASUREMENT', ARRAY['pt','en'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000013', 'book_relative_position', 'Book-relative position',
   'Excerpt start code-point offset divided by source document code-point length.', 'EXCERPT', 'NUMBER', 'ratio', 0, 1, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2'),
  ('73000000-0000-4000-8000-000000000014', 'chapter_relative_position', 'Chapter-relative position',
   'Excerpt start code-point offset relative to the containing chapter interval.', 'EXCERPT', 'NUMBER', 'ratio', 0, 1, 1,
   'MEASUREMENT', ARRAY['all'], 'deterministic-text-v2')
ON CONFLICT (code) DO NOTHING;
