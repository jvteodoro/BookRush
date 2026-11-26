ALTER TABLE reader_state.supervised_reading_cursor ADD COLUMN target_key TEXT;
ALTER TABLE reader_state.supervised_reading_audit ADD COLUMN target_key TEXT;
ALTER TABLE reader_state.supervised_reading_audit ADD COLUMN correlation_id TEXT;
