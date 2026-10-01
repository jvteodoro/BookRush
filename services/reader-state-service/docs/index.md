# reader-state-service

Reader library, progress and streak state. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 persist library, progress, bookmarks and reading-day projections per subject.

V4 adiciona recentes e sessões; updates são restringidos ao subject autenticado.

Every mutation requires the authenticated `(iss, sub)` principal. Opening a
book is an idempotent upsert in `reader_state.recent`, so retries from a mobile
client do not create duplicate rows. The controller test covers the SQL
upsert and the unauthenticated rejection path.
