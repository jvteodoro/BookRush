ALTER TABLE reporting.moderation_daily
    ADD COLUMN open_under_1_day BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN open_1_to_3_days BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN open_3_to_7_days BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN open_7_days_or_more BIGINT NOT NULL DEFAULT 0;
