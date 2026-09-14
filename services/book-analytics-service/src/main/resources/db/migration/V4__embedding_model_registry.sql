INSERT INTO analytics.embedding_model(id, code, model_name, model_version, dimension, pooling_strategy)
VALUES ('72000000-0000-4000-8000-000000000001','local-disabled','none','0',1,'NONE')
ON CONFLICT (code) DO NOTHING;
