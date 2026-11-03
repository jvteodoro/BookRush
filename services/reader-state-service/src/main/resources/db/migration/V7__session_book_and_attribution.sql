ALTER TABLE reader_state.session ADD COLUMN IF NOT EXISTS book_id UUID;
ALTER TABLE reader_state.session ADD COLUMN IF NOT EXISTS recommendation_item_id UUID;
ALTER TABLE reader_state.session ADD COLUMN IF NOT EXISTS impression_id UUID;
