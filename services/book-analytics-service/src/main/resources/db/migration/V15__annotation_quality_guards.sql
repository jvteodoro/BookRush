ALTER TABLE analytics.annotation_campaign
  ADD COLUMN IF NOT EXISTS disagreement_threshold INTEGER NOT NULL DEFAULT 2
    CHECK (disagreement_threshold BETWEEN 1 AND 4),
  ADD COLUMN IF NOT EXISTS quality_config_hash CHAR(64)
    CHECK (quality_config_hash IS NULL OR quality_config_hash ~ '^[0-9a-f]{64}$');

CREATE OR REPLACE FUNCTION analytics.prevent_published_dataset_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' OR OLD.status = 'PUBLISHED' THEN
    RAISE EXCEPTION 'published annotation dataset is immutable';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS annotation_dataset_version_immutable ON analytics.annotation_dataset_version;
CREATE TRIGGER annotation_dataset_version_immutable
  BEFORE UPDATE OR DELETE ON analytics.annotation_dataset_version
  FOR EACH ROW EXECUTE FUNCTION analytics.prevent_published_dataset_mutation();

CREATE OR REPLACE FUNCTION analytics.prevent_published_dataset_item_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM analytics.annotation_dataset_version
             WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.dataset_version_id ELSE NEW.dataset_version_id END
               AND status = 'PUBLISHED') THEN
    RAISE EXCEPTION 'published annotation dataset items are immutable';
  END IF;
  IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS annotation_dataset_item_immutable ON analytics.annotation_dataset_item;
CREATE TRIGGER annotation_dataset_item_immutable
  BEFORE INSERT OR UPDATE OR DELETE ON analytics.annotation_dataset_item
  FOR EACH ROW EXECUTE FUNCTION analytics.prevent_published_dataset_item_mutation();
