-- A null pair is quarantine. V1–V7 stored neither a realm nor evidence of JWT validation.
ALTER TABLE publisher.membership
  ADD COLUMN identity_issuer TEXT,
  ADD COLUMN identity_subject TEXT,
  ADD CONSTRAINT membership_technical_identity CHECK (
    (identity_issuer IS NULL AND identity_subject IS NULL) OR
    (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
     AND btrim(identity_issuer) <> '' AND btrim(identity_subject) <> '')
  );

ALTER TABLE publisher.submission
  ADD COLUMN identity_issuer TEXT,
  ADD COLUMN identity_subject TEXT,
  ADD CONSTRAINT submission_technical_identity CHECK (
    (identity_issuer IS NULL AND identity_subject IS NULL) OR
    (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
     AND btrim(identity_issuer) <> '' AND btrim(identity_subject) <> '')
  );

ALTER TABLE publisher.staged_upload
  ADD COLUMN identity_issuer TEXT,
  ADD COLUMN identity_subject TEXT,
  ADD CONSTRAINT staged_upload_technical_identity CHECK (
    (identity_issuer IS NULL AND identity_subject IS NULL) OR
    (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
     AND btrim(identity_issuer) <> '' AND btrim(identity_subject) <> '')
  );

ALTER TABLE publisher.rights_attestation
  ADD COLUMN identity_issuer TEXT,
  ADD COLUMN identity_subject TEXT,
  ADD CONSTRAINT rights_attestation_technical_identity CHECK (
    (identity_issuer IS NULL AND identity_subject IS NULL) OR
    (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
     AND btrim(identity_issuer) <> '' AND btrim(identity_subject) <> '')
  );

ALTER TABLE publisher.publication_review
  ADD COLUMN identity_issuer TEXT,
  ADD COLUMN identity_subject TEXT,
  ADD CONSTRAINT publication_review_technical_identity CHECK (
    (identity_issuer IS NULL AND identity_subject IS NULL) OR
    (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
     AND btrim(identity_issuer) <> '' AND btrim(identity_subject) <> '')
  );

ALTER TABLE publisher.published_work
  ADD COLUMN identity_issuer TEXT,
  ADD COLUMN identity_subject TEXT,
  ADD CONSTRAINT published_work_technical_identity CHECK (
    (identity_issuer IS NULL AND identity_subject IS NULL) OR
    (identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
     AND btrim(identity_issuer) <> '' AND btrim(identity_subject) <> '')
  );

CREATE UNIQUE INDEX membership_technical_identity_idx ON publisher.membership(identity_issuer, identity_subject)
  WHERE identity_issuer IS NOT NULL;
CREATE INDEX submission_technical_owner_idx
  ON publisher.submission(identity_issuer, identity_subject, created_at DESC);
CREATE INDEX staged_upload_technical_owner_idx
  ON publisher.staged_upload(submission_id, identity_issuer, identity_subject, finalized_at DESC);
CREATE INDEX published_work_technical_owner_idx
  ON publisher.published_work(identity_issuer, identity_subject, published_at DESC);

ALTER TABLE publisher.submission ADD COLUMN linked_by_workload TEXT;
ALTER TABLE publisher.rights_attestation
  ADD COLUMN claims_own_rights BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN holder_issuer TEXT GENERATED ALWAYS AS (CASE WHEN claims_own_rights THEN identity_issuer END) STORED,
  ADD COLUMN holder_subject TEXT GENERATED ALWAYS AS (CASE WHEN claims_own_rights THEN identity_subject END) STORED,
  ADD CONSTRAINT own_rights_require_technical_affirmer CHECK (NOT claims_own_rights OR identity_issuer IS NOT NULL);

-- The current attestation remains available to existing reporting. Prior statements are archived intact.
CREATE TABLE publisher.rights_attestation_history (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  attestation_id UUID NOT NULL REFERENCES publisher.rights_attestation(id),
  affirmed_by_subject TEXT NOT NULL,
  identity_issuer TEXT,
  identity_subject TEXT,
  claims_own_rights BOOLEAN NOT NULL,
  evidence_reference TEXT,
  affirmed_at TIMESTAMPTZ NOT NULL,
  superseded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION publisher.archive_rights_statement() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.evidence_reference IS DISTINCT FROM NEW.evidence_reference
     OR OLD.affirmed_by_subject IS DISTINCT FROM NEW.affirmed_by_subject
     OR OLD.claims_own_rights IS DISTINCT FROM NEW.claims_own_rights
     OR OLD.affirmed_at IS DISTINCT FROM NEW.affirmed_at THEN
    INSERT INTO publisher.rights_attestation_history(
      attestation_id, affirmed_by_subject, identity_issuer, identity_subject,
      claims_own_rights, evidence_reference, affirmed_at)
    VALUES (OLD.id, OLD.affirmed_by_subject, OLD.identity_issuer, OLD.identity_subject,
      OLD.claims_own_rights, OLD.evidence_reference, OLD.affirmed_at);
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER archive_rights_statement BEFORE UPDATE ON publisher.rights_attestation
  FOR EACH ROW EXECUTE FUNCTION publisher.archive_rights_statement();

CREATE TABLE publisher.identity_evidence (
  entity TEXT NOT NULL CHECK (entity IN (
    'membership','submission','staged_upload','rights_attestation','publication_review','published_work')),
  record_key TEXT NOT NULL CHECK (btrim(record_key) <> ''),
  legacy_subject TEXT NOT NULL CHECK (btrim(legacy_subject) <> ''),
  verified_issuer TEXT NOT NULL CHECK (btrim(verified_issuer) <> ''),
  verified_subject TEXT NOT NULL CHECK (verified_subject = legacy_subject),
  evidence_reference TEXT NOT NULL CHECK (btrim(evidence_reference) <> ''),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  recorded_by TEXT NOT NULL DEFAULT session_user,
  PRIMARY KEY (entity, record_key)
);

-- Apply per-record operator-reviewed provenance only. The original key and timestamps never change.
CREATE FUNCTION publisher.reconcile_legacy_identity() RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
  mapping RECORD;
  updated INTEGER;
  total INTEGER := 0;
BEGIN
  FOR mapping IN SELECT * FROM (VALUES
    ('membership','subject_key','subject_key'),
    ('submission','id','subject_key'),
    ('staged_upload','id','subject_key'),
    ('rights_attestation','id','affirmed_by_subject'),
    ('publication_review','id','reviewer_subject_key'),
    ('published_work','id','publisher_subject_key')
  ) AS rules(entity, record_key, legacy_subject)
  LOOP
    EXECUTE format(
      'UPDATE publisher.%I r SET identity_issuer=e.verified_issuer, identity_subject=e.verified_subject
        FROM publisher.identity_evidence e
        WHERE e.entity=$1 AND e.record_key=r.%I::text AND e.legacy_subject=r.%I
          AND r.identity_issuer IS NULL AND r.identity_subject IS NULL',
      mapping.entity, mapping.record_key, mapping.legacy_subject
    ) USING mapping.entity;
    GET DIAGNOSTICS updated = ROW_COUNT;
    total := total + updated;
  END LOOP;
  RETURN total;
END;
$$;

-- A known pair cannot be altered by any state transition. A fresh rights
-- assertion can attribute a previously quarantined statement only through
-- the archival trigger, which keeps the previous statement and its unknown pair.
CREATE FUNCTION publisher.prevent_identity_reassignment() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.identity_issuer IS NOT NULL AND
     (OLD.identity_issuer IS DISTINCT FROM NEW.identity_issuer
      OR OLD.identity_subject IS DISTINCT FROM NEW.identity_subject) THEN
    RAISE EXCEPTION 'technical identity is immutable' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_technical_identity BEFORE UPDATE OF identity_issuer, identity_subject ON publisher.membership
  FOR EACH ROW EXECUTE FUNCTION publisher.prevent_identity_reassignment();
CREATE TRIGGER immutable_technical_identity BEFORE UPDATE OF identity_issuer, identity_subject ON publisher.submission
  FOR EACH ROW EXECUTE FUNCTION publisher.prevent_identity_reassignment();
CREATE TRIGGER immutable_technical_identity BEFORE UPDATE OF identity_issuer, identity_subject ON publisher.staged_upload
  FOR EACH ROW EXECUTE FUNCTION publisher.prevent_identity_reassignment();
CREATE TRIGGER immutable_technical_identity BEFORE UPDATE OF identity_issuer, identity_subject ON publisher.rights_attestation
  FOR EACH ROW EXECUTE FUNCTION publisher.prevent_identity_reassignment();
CREATE TRIGGER immutable_technical_identity BEFORE UPDATE OF identity_issuer, identity_subject ON publisher.publication_review
  FOR EACH ROW EXECUTE FUNCTION publisher.prevent_identity_reassignment();
CREATE TRIGGER immutable_technical_identity BEFORE UPDATE OF identity_issuer, identity_subject ON publisher.published_work
  FOR EACH ROW EXECUTE FUNCTION publisher.prevent_identity_reassignment();
