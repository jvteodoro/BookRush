-- Non-VALID observations are explicit evidence that a model or input was not
-- available. They intentionally have no numeric/text/json value; only VALID
-- observations must carry exactly one typed value.
ALTER TABLE analytics.excerpt_feature
  DROP CONSTRAINT IF EXISTS excerpt_feature_check;

ALTER TABLE analytics.excerpt_feature
  ADD CONSTRAINT excerpt_feature_check
  CHECK (
    value_status <> 'VALID'
    OR num_nonnulls(numeric_value, text_value, json_value) = 1
  );
