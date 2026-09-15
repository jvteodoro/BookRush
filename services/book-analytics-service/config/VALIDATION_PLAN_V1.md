# Analytics V1 Validation and Bulk-Rollout Plan

1. **Gate 0 — Specification**: specs/configs in Git; immutable model revisions resolved; corpus snapshot identified.
2. **Gate 1 — Deterministic**: 10 books. Manually verify offsets, paragraphs, words, sentences, dialogue, questions and positions.
3. **Gate 2 — PT/EN linguistic**: 50 EN + 50 PT books. Inspect parse/readability/syllable/POS/entity failure rates and outliers.
4. **Gate 3 — Corpus frequency**: build one model per language; inspect token totals, vocabulary, OOV handling and checksum reproducibility.
5. **Gate 4 — BGE-M3**: >=100 books. Verify dimension=1024, L2 norms, cache/idempotence, long-chapter/document aggregation, semantic sanity and throughput/storage.
6. **Gate 5 — Narrative NLI**: 50 EN + 50 PT curated excerpts. Human review of 9 narrative dimensions and 7 emotions. Compare PT vs EN hypotheses for PT text.
7. **Gate 6 — Prototypes**: inspect top 20 and bottom 10 examples per concept, including cross-language behavior.
8. **Gate 7 — Topic classifier**: work-level split, no edition leakage, per-label support, precision/recall, macro/micro F1, PR-AUC and calibration review.
9. **Gate 8 — Pilot**: 500–1000 books end-to-end. Measure CPU/GPU time, DB/vector growth, cache, failure taxonomy, candidate reduction and top excerpt quality.
10. **Readiness review**: explicit GO/NO-GO. Never auto-start full-corpus processing as part of deployment.

Recalculate a feature whenever an identity component affecting it changes: normalized text hash, analyzer version, model revision, hypothesis/prototype version, tokenizer/parser version, corpus model, style normalization or config hash. Historical observations remain immutable.
