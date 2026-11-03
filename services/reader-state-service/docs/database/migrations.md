# Reader-state migrations

## V6 and V7 — feed-origin reading sessions

`V6__session_book_and_attribution.sql` adds nullable `book_id` to
`reader_state.session`, preserving compatibility with sessions created before
feed-origin attribution.

`V7__session_recommendation_correlation.sql` adds nullable
`recommendation_item_id` and `impression_id`. New sessions persist the IDs from
the authenticated request headers; ending a session reuses those IDs when the
client does not resend them. Direct, search and library sessions remain
uncorrelated (`NULL`). Both migrations are additive and do not rewrite or
delete existing sessions.
