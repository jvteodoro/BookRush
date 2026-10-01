-- V2 lexical/context observations. Additive; historical observations remain immutable.
INSERT INTO analytics.analyzer(id, code, analyzer_type, implementation, code_version, model_name, model_version)
VALUES
 ('70000000-0000-4000-8000-000000000003','lexical-statistics-v2','STATISTICAL','com.bookrush.analytics.features.StatisticalFeatureCalculator','2','corpus-frequency-v2','2'),
 ('70000000-0000-4000-8000-000000000004','bge-m3-v2','EMBEDDING','book-analytics-runtime','2','BAAI/bge-m3','5617a9f61b028005a4858fdac845db406aefb181')
ON CONFLICT (code) DO NOTHING;

INSERT INTO analytics.embedding_model(id, code, model_name, model_version, dimension, pooling_strategy, artifact_sha256)
VALUES ('72000000-0000-4000-8000-000000000002','bge-m3-dense-v1','BAAI/bge-m3','5617a9f61b028005a4858fdac845db406aefb181',1024,'mean-pooling-l2-normalized','b5e0ce3470abf5ef3831aa1bd5553b486803e83251590ab7ff35a117cf6aad38')
ON CONFLICT (code) DO NOTHING;

INSERT INTO analytics.feature_definition(id, code, name, description, scope, value_type, unit, version, semantic_category, language_codes, algorithm_version)
VALUES
 ('71000000-0000-4000-8000-000000000020','lex.ttr','Type-token ratio','Unique lexical types divided by tokens; unstable for short samples','EXCERPT','NUMBER','ratio',2,'MEASUREMENT',ARRAY['en','pt'],'lexical-v2'),
 ('71000000-0000-4000-8000-000000000021','lex.herdan_c','Herdan C','Log vocabulary divided by log token count','EXCERPT','NUMBER','ratio',2,'MEASUREMENT',ARRAY['en','pt'],'lexical-v2'),
 ('71000000-0000-4000-8000-000000000022','lex.yule_k','Yule K','Frequency concentration statistic','EXCERPT','NUMBER','index',2,'MEASUREMENT',ARRAY['en','pt'],'lexical-v2'),
 ('71000000-0000-4000-8000-000000000023','lex.hapax_ratio','Hapax ratio','Singleton token ratio','EXCERPT','NUMBER','ratio',2,'MEASUREMENT',ARRAY['en','pt'],'lexical-v2'),
 ('71000000-0000-4000-8000-000000000024','lex.lemma_type_count','Lemma type count','Distinct lemma count from linguistic runtime','EXCERPT','NUMBER','lemmas',2,'MEASUREMENT',ARRAY['en','pt'],'spacy-3.8'),
 ('71000000-0000-4000-8000-000000000025','semantic.context.document_cosine','Document context cosine','Excerpt/document embedding cosine similarity','EXCERPT','NUMBER','cosine',2,'MODEL_INFERENCE',ARRAY['all'],'bge-m3-v2'),
 ('71000000-0000-4000-8000-000000000026','semantic.context.chapter_cosine','Chapter context cosine','Excerpt/chapter embedding cosine similarity','EXCERPT','NUMBER','cosine',2,'MODEL_INFERENCE',ARRAY['all'],'bge-m3-v2')
ON CONFLICT (code) DO NOTHING;
