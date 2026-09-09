CREATE TABLE book_chapter (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES book(id) ON DELETE RESTRICT,
    edition_id UUID NOT NULL REFERENCES edition(id) ON DELETE RESTRICT,
    text_asset_version_id UUID NOT NULL REFERENCES book_asset_version(id) ON DELETE RESTRICT,
    chapter_key TEXT NOT NULL CHECK (btrim(chapter_key) <> ''),
    parent_chapter_id UUID REFERENCES book_chapter(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK (position >= 0),
    title TEXT NOT NULL,
    start_offset INTEGER NOT NULL CHECK (start_offset >= 0),
    end_offset INTEGER NOT NULL CHECK (end_offset >= start_offset),
    confidence VARCHAR(16) NOT NULL CHECK (confidence IN ('EXPLICIT','FALLBACK')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (text_asset_version_id, chapter_key),
    FOREIGN KEY (edition_id, book_id) REFERENCES edition(id, book_id) ON DELETE RESTRICT
);
CREATE INDEX book_chapter_edition_position_idx ON book_chapter(edition_id, position);
CREATE INDEX book_chapter_text_version_idx ON book_chapter(text_asset_version_id, start_offset, end_offset);
