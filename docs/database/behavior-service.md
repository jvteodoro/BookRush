# Behavior service database

`behavior-service` owns the `behavior` schema and runs its own Flyway history.
It stores append-only client/server facts in `behavior.events`; aggregate tables
are rebuildable projections and are never used as the event source of truth.

Migration V5 adds `behavior.daily_user_book`, keyed by UTC event day, the
authenticated `(iss, sub)` subject represented by `identity_subject`, and
`book_id`. It contains daily impressions, opens, likes, reads and validated
`READ_SESSION.activeSeconds`. The projection is rebuilt by
`POST /api/v1/behavior/aggregates/rebuild`; malformed duration values contribute
zero and are not allowed to abort a rebuild. `GET /api/v1/behavior/aggregates/me`
is authenticated and returns only the current principal's rows.

The table is a privacy-sensitive operational projection. It does not create a
product profile, infer ownership of a book, or replace append-only events.

Migration V6 adds `behavior.outbox`. Each newly accepted event creates one
outbox row with the same event id; duplicate `event_key` retries create no new
row. A publisher can claim pending rows and set `published_at` after delivery,
while the append-only event remains the source of truth.
