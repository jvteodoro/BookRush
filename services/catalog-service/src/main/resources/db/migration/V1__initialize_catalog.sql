-- Flyway creates/owns catalog; existing public tables are deliberately untouched.
COMMENT ON SCHEMA catalog IS 'BookRush catalog-service: bibliographic metadata, never book binaries';

-- All mutable catalog entities use the database clock, including non-JPA writers.
CREATE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;
