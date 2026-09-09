-- Keep V7/V9 immutable after deployment. This migration repairs the
-- verification transition rules without changing their Flyway checksums.

ALTER TABLE book_asset_version
  DROP CONSTRAINT IF EXISTS asset_version_verification_check;

CREATE OR REPLACE FUNCTION enforce_asset_version_verification()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.status = 'AVAILABLE' AND NEW.verified_at IS NULL THEN
    NEW.verified_at := COALESCE(NEW.created_at, clock_timestamp());
    NEW.verification_method := COALESCE(NEW.verification_method, 'LEGACY_INSERT');
  ELSIF TG_OP = 'INSERT' AND NEW.status <> 'AVAILABLE' AND NEW.availability_status = 'AVAILABLE' THEN
    NEW.availability_status := NEW.status;
  END IF;
  IF TG_OP = 'UPDATE' AND OLD.status <> 'AVAILABLE' AND NEW.status = 'AVAILABLE' AND NEW.verified_at IS NULL THEN
    IF NEW.sha256 IS NOT NULL AND NEW.size_bytes IS NOT NULL AND NEW.size_bytes >= 0 AND NEW.content_type IS NOT NULL THEN
      NEW.verified_at := COALESCE(NEW.updated_at, clock_timestamp());
      NEW.verification_method := 'LEGACY_METADATA';
    ELSE
      RAISE EXCEPTION 'available asset versions require physical verification'
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS asset_version_verification_trigger ON book_asset_version;
CREATE TRIGGER asset_version_verification_trigger
  BEFORE INSERT OR UPDATE ON book_asset_version
  FOR EACH ROW EXECUTE FUNCTION enforce_asset_version_verification();

ALTER TABLE book_asset_version
  ADD CONSTRAINT asset_version_verification_check CHECK
    (availability_status <> 'AVAILABLE' OR
      (verified_at IS NOT NULL AND content_type IS NOT NULL AND size_bytes IS NOT NULL AND size_bytes >= 0
       AND sha256 IS NOT NULL AND sha256 ~ '^[0-9a-f]{64}$')) NOT VALID;
ALTER TABLE book_asset_version VALIDATE CONSTRAINT asset_version_verification_check;
