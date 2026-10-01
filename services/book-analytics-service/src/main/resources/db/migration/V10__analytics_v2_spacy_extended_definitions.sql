INSERT INTO analytics.feature_definition(id,code,name,description,scope,value_type,unit,min_value,max_value,version,semantic_category,language_codes,algorithm_version) VALUES
 ('74000000-0000-4000-8000-000000000013','dependency_depth_p90','Dependency depth p90','90th percentile dependency depth from spaCy parse.','EXCERPT','NUMBER','edges',0,NULL,2,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
 ('74000000-0000-4000-8000-000000000014','entity_density_per_100_words','Entity density','Named entities per 100 tokens.','EXCERPT','NUMBER','entities_per_100_words',0,NULL,2,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
 ('74000000-0000-4000-8000-000000000015','person_entity_density_per_100_words','Person entity density','PERSON entities per 100 tokens.','EXCERPT','NUMBER','persons_per_100_words',0,NULL,2,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2'),
 ('74000000-0000-4000-8000-000000000016','subordinate_clause_ratio','Subordinate clause ratio','Subordinate dependency relations divided by tokens.','EXCERPT','NUMBER','ratio',0,1,2,'MEASUREMENT',ARRAY['pt','en'],'spacy-v2')
ON CONFLICT(code) DO NOTHING;
