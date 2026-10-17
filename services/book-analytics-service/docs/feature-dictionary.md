# Feature dictionary V1

The service separates three kinds of output:

* **Measurements** (`struct.*`, `lexical.*`, `readability.*`) are deterministic or statistical observations of the exact text version.
* **Model scores** (`narrative.*`, `emotion.*`, `semantic.prototype.*`) are observations from a pinned model. NLI stores entailment, neutral and contradiction, then derives support and confidence with a guarded denominator. These values are not behavioral probabilities.

Semantic prototype scores are persisted at all available structural scopes:
`semantic.prototype.<concept>.raw_cosine` for excerpts, and
`semantic.prototype.<concept>.raw_cosine.chapter` / `.document` for chapter and
document embeddings. They are signed raw cosine similarities in `[-1, 1]`;
they are intentionally not sigmoid/softmax values and must not be interpreted
as probabilities. Chapter rows use `chapter_id`; document rows use the input
asset version associated with the analysis run.
* **Product-selection scores** (`excerpt.rank.*`) only order candidates. The bootstrap ranker is explicitly unvalidated and must not be called quality, engagement or interestingness.

All observations carry analyzer/configuration/model lineage and point to the same `book_asset_version` as the excerpt. Status values include `UNSUPPORTED`, `INVALID_INPUT`, `INSUFFICIENT_SAMPLE`, `MODEL_UNAVAILABLE` and `ERROR`; unsupported linguistic metrics are never represented as zero.

Structural offsets are Unicode code points and intervals are half-open. Reprocessing with a new analyzer, model, configuration or text version creates a new observation and preserves the old one.

The canonical machine-readable feature list is [`config/analytics-spec-v1.yaml`](../config/analytics-spec-v1.yaml). Formulas and model revisions are validated by `scripts/validate-analytics-specs.py` before CI accepts a change.
