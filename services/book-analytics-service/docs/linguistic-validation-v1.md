# Linguistic validation V1

The linguistic boundary accepts only `en` and `pt` for the full PT/EN model
pipeline. Catalog language is evidence, not a value that the analytics worker
may silently rewrite.

## Input and status semantics

- null or blank language is `INVALID_INPUT`;
- language values are trimmed and normalized to lowercase before comparison;
- languages outside `en`/`pt` are `UNSUPPORTED`, never zero-valued;
- null or blank text is rejected as `INVALID_INPUT` before a model call;
- an unavailable local model is `MODEL_UNAVAILABLE` and preserves the warning;
- a valid runtime prediction stores detected language, confidence and agreement
  with the catalog value.

The implementation is covered by `LanguageValidationTest`, `LanguageEvidenceTest`
and the real-model harness `scripts/test-analytics-models.sh`. The latter is
the integration evidence for the prepared fastText/spaCy artifacts; unit tests
must not claim model quality.

## Operational checks

```bash
mvn -q -f services/book-analytics-service/pom.xml test
ANALYTICS_MODEL_VOLUME=bookrush_analytics_models bash scripts/test-analytics-models.sh
```

The model volume is mounted read-only. Unsupported language and malformed/short
input cases remain visible through `value_status` and `warning` fields.
