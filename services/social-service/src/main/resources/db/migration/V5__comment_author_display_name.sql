ALTER TABLE social.comments
  ADD COLUMN IF NOT EXISTS author_display_name TEXT;

COMMENT ON COLUMN social.comments.author_display_name IS
  'Display-name snapshot from the validated product JWT; subject_key remains the identity key.';
