ALTER TABLE family.membership
    ADD COLUMN invitation_id uuid UNIQUE REFERENCES family.invitation,
    ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0);
