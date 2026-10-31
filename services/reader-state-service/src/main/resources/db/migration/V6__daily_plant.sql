CREATE TABLE reader_state.daily_plant (
  subject_key TEXT PRIMARY KEY,
  stage SMALLINT NOT NULL DEFAULT 0 CHECK (stage BETWEEN 0 AND 5),
  sad BOOLEAN NOT NULL DEFAULT FALSE,
  start_day DATE NOT NULL,
  goal_applied BOOLEAN NOT NULL DEFAULT FALSE,
  streak_days INTEGER NOT NULL DEFAULT 0 CHECK (streak_days >= 0),
  CHECK (stage > 0 OR NOT sad)
);

CREATE TABLE reader_state.plant_reading_day (
  subject_key TEXT NOT NULL,
  day DATE NOT NULL,
  active_seconds INTEGER NOT NULL DEFAULT 0 CHECK (active_seconds >= 0),
  PRIMARY KEY (subject_key, day)
);

CREATE TABLE reader_state.plant_reading_event (
  subject_key TEXT NOT NULL,
  event_id UUID NOT NULL,
  PRIMARY KEY (subject_key, event_id)
);
