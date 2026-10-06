ALTER TABLE reader_state.progress
  ADD COLUMN IF NOT EXISTS text_asset_version_id UUID,
  ADD COLUMN IF NOT EXISTS chapter_id UUID,
  ADD COLUMN IF NOT EXISTS locator JSONB;

ALTER TABLE reader_state.bookmarks
  ADD COLUMN IF NOT EXISTS text_asset_version_id UUID,
  ADD COLUMN IF NOT EXISTS chapter_id UUID,
  ADD COLUMN IF NOT EXISTS locator JSONB;

ALTER TABLE reader_state.progress
  ADD CONSTRAINT reader_progress_locator_object_check
  CHECK (locator IS NULL OR jsonb_typeof(locator) = 'object') NOT VALID;

ALTER TABLE reader_state.bookmarks
  ADD CONSTRAINT reader_bookmark_locator_object_check
  CHECK (locator IS NULL OR jsonb_typeof(locator) = 'object') NOT VALID;

ALTER TABLE reader_state.progress
  VALIDATE CONSTRAINT reader_progress_locator_object_check;

ALTER TABLE reader_state.bookmarks
  VALIDATE CONSTRAINT reader_bookmark_locator_object_check;

CREATE INDEX IF NOT EXISTS reader_progress_subject_updated_idx
  ON reader_state.progress(subject_key, updated_at DESC);
CREATE INDEX IF NOT EXISTS reader_bookmarks_version_idx
  ON reader_state.bookmarks(text_asset_version_id, chapter_id)
  WHERE text_asset_version_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS reader_state.plant_state (
  subject_key TEXT PRIMARY KEY,
  formula_version VARCHAR(64) NOT NULL,
  stage SMALLINT NOT NULL CHECK (stage >= 0),
  streak_days INTEGER NOT NULL DEFAULT 0 CHECK (streak_days >= 0),
  total_reading_minutes BIGINT NOT NULL DEFAULT 0 CHECK (total_reading_minutes >= 0),
  measured_until DATE NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
