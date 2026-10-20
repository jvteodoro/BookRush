# PFV1 candidate retrieval

`bookrush-pfv1-310` separates candidate retrieval from ranking in the
recommendation service. The feed no longer treats catalog page zero as the
candidate universe.

`CatalogCandidateRetriever` reads catalog pages with explicit bounds:

- `FEED_RETRIEVAL_MAX_CANDIDATES` (default `500`)
- `FEED_RETRIEVAL_PAGE_SIZE` (default `100`, capped at `100`)
- `FEED_RETRIEVAL_MAX_PAGES` (default `5`)
- `FEED_RETRIEVAL_VERSION` (default `catalog-page-v1`)

The retriever preserves the first occurrence of each book ID, accepts books
without an embedding, and stops at the configured candidate limit or a short
catalog page. The resulting `CandidateSet` carries version, configuration hash,
source, and a deterministic list of IDs for replay/provenance.

The implementation is intentionally catalog-backed and bounded. Future PFV1
retrievers can combine semantic, global, trending, and exploration sources
behind `CandidateRetriever` without coupling those changes to the ranker.

`CandidateUnionBuilder` now provides that source-union contract: each provider
supplies named candidates, semantic candidates are ordered by cosine similarity
when a `UserTasteProfile` is available, quotas prevent one source from
monopolizing the pool, and duplicate books retain all contributing source names.
The union is deterministic and bounded. The current catalog API has no trending
or embedding-vector serving endpoint, so the live adapter remains catalog-backed
until those providers are connected; no fake trending signal is generated.

Validation is covered by `CatalogCandidateRetrieverTest`, including a book
returned beyond the former first-page boundary, duplicate IDs, missing
embeddings, and bounded pagination.

## Repeat-exposure fatigue

`ExposureFatigue` uses only viewable impressions for the initial signal. A
recently viewable book receives a bounded penalty proportional to its count;
the penalty decays exponentially from the last viewable timestamp. A request
that was never marked viewable therefore contributes no fatigue. The
recommendation ledger query is bounded to the current candidate IDs and the
lookup indexes are installed by `V5__fatigue_lookup_indexes.sql`.

The effective score and the persisted `reason` include the fatigue component,
so a ranking decision can be explained and replayed. Configuration is
versioned through `FEED_FATIGUE_*` and the behavior is covered by unit tests.

`PersonalizedRankerV1` is the next serving contract. It combines normalized
affinity, quality, trend, exploration, and fatigue components and returns the
full `Breakdown` alongside the score. Missing features use an explicit policy
(`RENORMALIZE`, `ZERO`, or `EXCLUDE`), and tie-breaking is deterministic from a
seed. Its weights are bootstrap/unvalidated and must not be interpreted as
probabilities. The class is covered independently until the live profile and
source providers are wired into the HTTP feed.

`BookDiversityMmr` provides the post-score, book-level diversity step. It uses
`lambda * score - (1-lambda) * maxCosineToSelected`, has deterministic ID
tie-breaking, and records the selected candidate's redundancy/rationale. A
missing embedding has a documented zero-similarity fallback and never produces
NaN. This is intentionally separate from excerpt-level analytics MMR.
