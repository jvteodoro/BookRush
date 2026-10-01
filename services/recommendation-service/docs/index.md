# recommendation-service

Versioned heuristic recommendations. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

V2 persiste requests e impressions do feed, incluindo versão do modelo e posição. A escrita ocorre no mesmo serviço do ranking.

The Compose smoke validates startup with the recommendation URL nested under
`bookrush` and runs the migration against the isolated database. This service
remains the sole writer for its recommendation schema.

Feed requests require an authenticated principal and persist that subject in
the request ledger. Each item still carries request ID, impression ID, model
version and rank for later attribution.
