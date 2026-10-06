CREATE TABLE IF NOT EXISTS reader_profile.profile_privacy (
  subject_key TEXT PRIMARY KEY REFERENCES reader_profile.profile(subject_key) ON DELETE RESTRICT,
  profile_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
    CHECK (profile_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  activity_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
    CHECK (activity_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  library_visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'
    CHECK (library_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  recent_visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'
    CHECK (recent_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  social_graph_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
    CHECK (social_graph_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO reader_profile.profile_privacy (subject_key, profile_visibility, activity_visibility, social_graph_visibility)
SELECT subject_key,
       CASE WHEN is_public THEN 'PUBLIC' ELSE 'PRIVATE' END,
       CASE WHEN is_public THEN 'PUBLIC' ELSE 'PRIVATE' END,
       CASE WHEN is_public THEN 'PUBLIC' ELSE 'PRIVATE' END
FROM reader_profile.profile
ON CONFLICT (subject_key) DO NOTHING;

CREATE INDEX IF NOT EXISTS profile_privacy_profile_visibility_idx
  ON reader_profile.profile_privacy(profile_visibility)
  WHERE profile_visibility <> 'PRIVATE';
