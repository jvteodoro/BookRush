CREATE TABLE IF NOT EXISTS recommendation.experiment_assignment (
    experiment_id VARCHAR(96) NOT NULL,
    subject_key TEXT NOT NULL,
    variant VARCHAR(32) NOT NULL CHECK (variant IN ('CONTROL', 'TREATMENT')),
    assignment_version VARCHAR(96) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (experiment_id, subject_key)
);

CREATE INDEX IF NOT EXISTS recommendation_experiment_assignment_variant_idx
  ON recommendation.experiment_assignment(experiment_id, variant, assigned_at);
