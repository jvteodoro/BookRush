-- V2 Tier 1 linguistic observations. Additive; V1-V7 remain immutable.
INSERT INTO analytics.analyzer(id,code,analyzer_type,implementation,code_version)
VALUES ('70000000-0000-4000-8000-000000000008','spacy-linguistic-v2','STATISTICAL','book-analytics-runtime','1') ON CONFLICT(code) DO NOTHING;
INSERT INTO analytics.feature_definition(id,code,name,description,scope,value_type,unit,min_value,max_value,version,semantic_category,language_codes,algorithm_version) VALUES
('74000000-0000-4000-8000-000000000001','unique_lemma_ratio','Unique lemma ratio','Distinct lowercased spaCy lemmas divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000002','noun_ratio','Noun ratio','Universal POS NOUN tokens divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000003','verb_ratio','Verb ratio','Universal POS VERB tokens divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000004','adjective_ratio','Adjective ratio','Universal POS ADJ tokens divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000005','adverb_ratio','Adverb ratio','Universal POS ADV tokens divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000006','pronoun_ratio','Pronoun ratio','Universal POS PRON tokens divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000007','finite_verb_density','Finite verb density','Finite verbs divided by tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000008','dependency_depth_mean','Mean dependency depth','Mean token dependency depth with root depth zero.','EXCERPT','NUMBER','edges',0,NULL,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000009','dependency_distance_mean','Mean dependency distance','Mean absolute head-token distance.','EXCERPT','NUMBER','tokens',0,NULL,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000010','entity_count','Entity count','spaCy named entity span count.','EXCERPT','NUMBER','entities',0,NULL,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000011','unique_entity_count','Unique entity count','Distinct normalized named entity texts.','EXCERPT','NUMBER','entities',0,NULL,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
('74000000-0000-4000-8000-000000000012','opening_pronoun_ratio','Opening pronoun ratio','Pronouns among first configured 20 tokens.','EXCERPT','NUMBER','ratio',0,1,1,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2')
ON CONFLICT(code) DO NOTHING;
