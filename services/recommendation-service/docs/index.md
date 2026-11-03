# recommendation-service

Versioned heuristic recommendations. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

V2 persiste requests e impressions do feed, incluindo versão do modelo e posição. A escrita ocorre no mesmo serviço do ranking.

The Compose smoke validates startup with the recommendation URL nested under
`bookrush` and runs the migration against the isolated database. This service
remains the sole writer for its recommendation schema.

Feed requests require an authenticated principal and persist that subject in
the request ledger. Each item still carries request ID, impression ID, model
version and rank for later attribution.

`POST /api/v1/recommendations/impressions/{impressionId}/viewable` marks an
impression only when the authenticated subject owns the originating request.
The browser calls this after its viewability threshold, so non-visible feed
items are not treated as negative outcomes. The endpoint is idempotent.

The viewability event includes `viewable=true`, `recommendationItemId` and
`impressionId`; this is the explicit input consumed by behavior's
`viewable_impression_count` projection. Personalized ranking obtains candidate
embeddings in bounded cached batches, computes candidate affinity as cosine
similarity against `UserTasteProfile.vector()`, and supplies those same
candidate embeddings to book-level MMR. Exploration uses a request seed stored
in the ledger; no complete embedding vector is persisted in the ledger.
Trending candidates come from behavior's authenticated `trending-v1` serving
endpoint and are merged with semantic/global candidates; recommendation does
not query the behavior schema directly.
Each ledger item also records its contributing candidate sources, union version
and configuration hash alongside the candidate-set hash for replay.

The dataset handoff reads recommendation decisions and behavior outcomes from
separate read-only database URLs and joins them locally by explicit
`recommendation_item_id`; recommendation never queries the behavior schema.
The export requires `BEHAVIOR_DATABASE_URL`, applies a seven-day observation
window capped by the dataset cutoff, and emits separate outcome labels.

Requests de feed e impressões viewable emitem eventos server-side ao
behavior-service com o bearer autenticado e retry limitado. O behavior-service
é responsável pela persistência idempotente e pelo outbox transacional.

## Analytics excerpts

The optional `excerpt` field is enriched in this service through a technical
client-credentials token. The user bearer is never sent to analytics. Enable
`FEED_EXCERPTS_ENABLED` and a non-zero `FEED_EXCERPTS_PERCENTAGE` only after
reconciling `bookrush-recommendation-s2s` in the administrative realm and
providing its protected secret in `.env`.

When analytics ranking is enabled and its S2S credentials are configured, the
feed is fail-closed: a book must have both an eligible analytics score and a
non-empty persisted excerpt to enter the response. Missing processing,
expired credentials and structural exclusions therefore remove the book from
that feed instead of producing a card with a misleading placeholder. If the
analytics ranking flag is disabled, the legacy heuristic feed remains
available for controlled development environments.

The reader BFF allows up to 15 seconds for the bounded recommendation batch;
this avoids converting normal batch latency into a 502 while preserving the
recommendation service's own bounded analytics clients.

## Personalized Feed V1

The safe default remains `FEED_PERSONALIZED_MODE=OFF`, serving the existing
analytics-aware/heuristic path. `SHADOW` calculates `PERSONALIZED_V1` without
changing the control response. `ON` serves it only after behavior, snapshot,
coverage and confidence gates pass. `FEED_PERSONALIZED_ROLLOUT_PERCENTAGE`
provides deterministic staged activation and setting the mode back to `OFF` is
the immediate rollback. See
`docs/recommendation/personalized-feed-v1-operations.md` for replay, metrics
and the read-only dataset handoff. In `SHADOW`, the service records a bounded
timer plus overlap and aggregate rank movement; these measurements never use
subject or book IDs as metric labels.

The `feed.personalized` properties use Spring's mutable configuration binding
so the service starts correctly in Docker and in the production profile. The
binding is covered by a context-property test; invalid rollout values are
handled by the gate's validation rather than changing the default `OFF` mode.
