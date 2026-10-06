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

## Analytics excerpts

The optional `excerpt` field is enriched in this service through a technical
client-credentials token. The user bearer is never sent to analytics. Enable
`FEED_EXCERPTS_ENABLED` and a non-zero `FEED_EXCERPTS_PERCENTAGE` only after
reconciling `bookrush-recommendation-s2s` in the administrative realm and
providing its protected secret in `.env`.

Missing analytics, expired credentials, missing processing and structural
exclusions degrade to a normal feed item. The BFF passes the nullable field
through and the browser never calls an analytics endpoint.
