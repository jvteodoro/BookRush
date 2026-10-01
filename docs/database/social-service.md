# Social service database

`social-service` owns the `social` schema. Likes, follows, comments, reports
and shares are keyed by the authenticated opaque subject from the request
principal. Anonymous writes are rejected with `401`; the service never accepts
an identity supplied in a request body or path as the actor. Comment and report
payloads are bounded by validation and the V4 share ledger records only the
book, channel and subject, not tokens or free-form private data.

Reports remain `OPEN` until an explicitly authorized moderation workflow changes
their status. This schema is not a user profile store and does not grant access
to catalog assets.
