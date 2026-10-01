CREATE TABLE analytics.annotation_campaign (
  id uuid PRIMARY KEY, code varchar(120) NOT NULL UNIQUE, name text NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'DRAFT', protocol_version varchar(64) NOT NULL,
  target_annotations_per_item integer NOT NULL DEFAULT 3 CHECK (target_annotations_per_item BETWEEN 1 AND 10),
  language_policy jsonb NOT NULL DEFAULT '{}'::jsonb, sampling_config jsonb NOT NULL DEFAULT '{}'::jsonb,
  sampling_config_hash char(64) NOT NULL CHECK (sampling_config_hash ~ '^[0-9a-f]{64}$'),
  created_by_subject text NOT NULL, version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status IN ('DRAFT','RUNNING','PAUSED','COMPLETED','CANCELLED'))
);
CREATE TABLE analytics.annotation_campaign_item (
  id uuid PRIMARY KEY, campaign_id uuid NOT NULL REFERENCES analytics.annotation_campaign(id) ON DELETE RESTRICT,
  excerpt_id uuid NOT NULL REFERENCES analytics.excerpt(id) ON DELETE RESTRICT,
  sampling_stratum varchar(120) NOT NULL, sampling_reason text NOT NULL, priority integer NOT NULL DEFAULT 0,
  target_annotations integer NOT NULL DEFAULT 3 CHECK (target_annotations BETWEEN 1 AND 10),
  state varchar(16) NOT NULL DEFAULT 'PENDING', created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(campaign_id, excerpt_id), CHECK (state IN ('PENDING','IN_PROGRESS','COMPLETE','SKIPPED'))
);
CREATE TABLE analytics.annotation_assignment (
  id uuid PRIMARY KEY, campaign_item_id uuid NOT NULL REFERENCES analytics.annotation_campaign_item(id) ON DELETE RESTRICT,
  annotator_subject text NOT NULL, claimed_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE', version bigint NOT NULL DEFAULT 0,
  UNIQUE(campaign_item_id, annotator_subject), CHECK(status IN ('ACTIVE','EXPIRED','COMPLETED','RELEASED'))
);
CREATE TABLE analytics.annotation (
  id uuid PRIMARY KEY, campaign_item_id uuid NOT NULL REFERENCES analytics.annotation_campaign_item(id) ON DELETE RESTRICT,
  annotator_subject text NOT NULL, status varchar(20) NOT NULL DEFAULT 'OPEN', confidence integer CHECK(confidence BETWEEN 1 AND 5),
  first_screen_hook integer CHECK(first_screen_hook BETWEEN 1 AND 5), primary_locked_at timestamptz,
  context_revealed_at timestamptz, submitted_at timestamptz, elapsed_ms bigint, protocol_version varchar(64) NOT NULL,
  client_version varchar(64), version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(campaign_item_id, annotator_subject), CHECK(status IN ('OPEN','LOCKED','SUBMITTED','SKIPPED'))
);
CREATE TABLE analytics.annotation_dimension_value (
  annotation_id uuid NOT NULL REFERENCES analytics.annotation(id) ON DELETE RESTRICT, dimension_code varchar(32) NOT NULL,
  stage varchar(16) NOT NULL, ordinal_value integer NOT NULL CHECK(ordinal_value BETWEEN 1 AND 5),
  PRIMARY KEY(annotation_id, dimension_code, stage), CHECK(stage IN ('FIRST_SCREEN','FULL_EXCERPT'))
);
CREATE TABLE analytics.annotation_failure_tag (annotation_id uuid NOT NULL REFERENCES analytics.annotation(id) ON DELETE RESTRICT, tag varchar(48) NOT NULL, PRIMARY KEY(annotation_id,tag));
CREATE TABLE analytics.annotation_context_diagnostic (
  annotation_id uuid PRIMARY KEY REFERENCES analytics.annotation(id) ON DELETE RESTRICT,
  context_required boolean NOT NULL, severity integer CHECK(severity BETWEEN 1 AND 5), notes text, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE analytics.annotation_event (
  id uuid PRIMARY KEY, annotation_id uuid REFERENCES analytics.annotation(id) ON DELETE RESTRICT,
  campaign_item_id uuid NOT NULL REFERENCES analytics.annotation_campaign_item(id) ON DELETE RESTRICT,
  event_type varchar(32) NOT NULL, actor_subject text NOT NULL, payload jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX annotation_event_item_idx ON analytics.annotation_event(campaign_item_id,created_at);
CREATE TABLE analytics.annotation_adjudication (
  id uuid PRIMARY KEY, campaign_item_id uuid NOT NULL REFERENCES analytics.annotation_campaign_item(id) ON DELETE RESTRICT,
  reviewer_subject text NOT NULL, dimension_code varchar(32), ordinal_value integer CHECK(ordinal_value BETWEEN 1 AND 5), rationale text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE analytics.annotation_dataset_version (
  id uuid PRIMARY KEY, campaign_id uuid NOT NULL REFERENCES analytics.annotation_campaign(id) ON DELETE RESTRICT,
  version_number integer NOT NULL, manifest_sha256 char(64) NOT NULL CHECK(manifest_sha256 ~ '^[0-9a-f]{64}$'), status varchar(16) NOT NULL DEFAULT 'PUBLISHED',
  artifact_uri text, created_by_subject text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(campaign_id,version_number), CHECK(status IN ('PUBLISHED','REVOKED'))
);
CREATE TABLE analytics.annotation_dataset_item (
  dataset_version_id uuid NOT NULL REFERENCES analytics.annotation_dataset_version(id) ON DELETE RESTRICT,
  campaign_item_id uuid NOT NULL REFERENCES analytics.annotation_campaign_item(id) ON DELETE RESTRICT,
  raw_labels jsonb NOT NULL, adjudicated_labels jsonb, PRIMARY KEY(dataset_version_id,campaign_item_id)
);
