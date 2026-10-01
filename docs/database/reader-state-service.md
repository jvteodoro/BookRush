# Reader state database

`reader-state-service` owns the `reader_state` schema and all state is keyed by
the authenticated opaque subject, never by an email supplied by the client.
Library, progress, recent books, bookmarks and sessions are persisted in
PostgreSQL. The `reading_day` table is a rebuildable daily projection used by
`GET /api/v1/reader/streak`.

Ending a session is idempotently restricted to the current subject and an open
session. Its non-negative `activeSeconds` value contributes whole minutes to
the current UTC database day. A missing or malformed duration is treated as
zero. The service does not expose another user's state and does not infer a
profile or recommendation from this projection.
