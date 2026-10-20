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
