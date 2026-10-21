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

The behavior service now runs `DataQualityReconciliation` periodically. It
records bounded gauges for event count, projected subjects, attributed
outcomes, orphaned correlations, and projection age, plus a healthy/issues
counter. An orphaned correlation is retained and reported as an issue; it is
never silently treated as a positive signal. The report is deliberately scoped
to behavior-owned data and does not query recommendation tables cross-schema.

Operational interpretation: `healthy` with zero attributed rows means there is
no observed correlated outcome yet, not a successful recommendation; a
positive `orphaned` gauge means collection/rebuild needs investigation. A
non-zero `projection_age_seconds` measures lag between the newest event and
the serving projection. These low-cardinality metrics can be charted directly
from the behavior-service Prometheus endpoint.

`ExplorationPolicy` replaces implicit hash-only lucky ordering with an explicit
epsilon/slot policy. It is configurable, bounded by a quota, deterministic for
the same request seed and policy version, excludes already selected IDs, and
returns an auditable reason/draw. It is not a contextual bandit and does not
claim engagement probabilities; the simulation test checks approximate quota
behavior without production traffic.

`BookDiversityMmr` provides the post-score, book-level diversity step. It uses
`lambda * score - (1-lambda) * maxCosineToSelected`, has deterministic ID
tie-breaking, and records the selected candidate's redundancy/rationale. A
missing embedding has a documented zero-similarity fallback and never produces
NaN. This is intentionally separate from excerpt-level analytics MMR.

## Personalized serving gate

`PersonalizedFeedGate` is the rollout boundary for PFV1. It is configured with
`FEED_PERSONALIZED_MODE=OFF|SHADOW|ON` and minimum `coverage`/`confidence`
thresholds. `OFF` keeps the existing analytics-aware/heuristic feed; `SHADOW`
records the intended path without changing the response; `ON` may only proceed
when behavior signals, the semantic snapshot and a `READY` profile satisfy the
configured thresholds. Missing prerequisites are classified and fall back
without a 5xx.

The recommendation controller now loads a bounded profile through the behavior
service S2S endpoint and the analytics recommendation snapshot. The behavior
client uses the dedicated `bookrush-recommendation-behavior-s2s` client,
short-lived client-credentials tokens and a per-subject two-minute cache. The
analytics request asks for embeddings only for this profile path; ordinary
score requests remain metadata-only. Missing behavior, missing embeddings,
timeouts or malformed rows are fail-safe and produce `FALLBACK` without a 5xx.

When `ON` is selected and the configured coverage/confidence thresholds are
met, `PersonalizedRankerV1` combines bounded affinity, analytics quality and
exposure fatigue, then `BookDiversityMmr` applies book-level diversity. The
exploration choice is deterministic from the request seed and is recorded as a
reason, rather than silently changing the ranking policy. `SHADOW` computes
the gate/profile path but keeps the existing response ranking.

The request ledger stores the personalization path/reason, candidate-set hash,
ranker/profile/MMR configuration hashes and exploration policy version. Each
personalized item also stores the explainable ranker components and MMR
rationale in its JSON reason. `bookrush.feed.personalized.path` and the
behavior/profile timers provide low-cardinality rollout observability.
