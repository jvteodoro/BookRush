INSERT INTO analytics.analyzer(id, code, analyzer_type, implementation, code_version)
VALUES ('70000000-0000-4000-8000-000000000001','deterministic-text-v1','DETERMINISTIC','com.bookrush.analytics.features.TextFeatureCalculator','1')
ON CONFLICT (code) DO NOTHING;
INSERT INTO analytics.feature_definition(id, code, name, description, scope, value_type, unit, version)
VALUES
('71000000-0000-4000-8000-000000000001','word_count','Word count','Unicode-aware token count','EXCERPT','NUMBER','words',1),
('71000000-0000-4000-8000-000000000002','sentence_count','Sentence count','Boundary estimate for supported punctuation','EXCERPT','NUMBER','sentences',1),
('71000000-0000-4000-8000-000000000003','question_ratio','Question ratio','Questions divided by estimated sentences','EXCERPT','NUMBER','ratio',1),
('71000000-0000-4000-8000-000000000004','dialogue_ratio','Dialogue ratio','Characters inside quote delimiters divided by code points','EXCERPT','NUMBER','ratio',1),
('71000000-0000-4000-8000-000000000005','estimated_read_time','Estimated read time','Word count divided by 200 words/minute','EXCERPT','NUMBER','minutes',1)
ON CONFLICT (code) DO NOTHING;
