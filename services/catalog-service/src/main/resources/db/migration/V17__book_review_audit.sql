CREATE TABLE book_review_audit (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES book(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('APPROVED', 'REVOKED')),
    previous_status VARCHAR(24) NOT NULL CHECK (previous_status IN ('ACTIVE', 'INACTIVE', 'MERGED', 'REVIEW_REQUIRED')),
    new_status VARCHAR(24) NOT NULL CHECK (new_status IN ('ACTIVE', 'INACTIVE', 'MERGED', 'REVIEW_REQUIRED')),
    actor_subject TEXT NOT NULL CHECK (btrim(actor_subject) <> ''),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX book_review_audit_book_created_idx
    ON book_review_audit(book_id, created_at DESC);

CREATE INDEX book_review_audit_created_idx
    ON book_review_audit(created_at DESC);
