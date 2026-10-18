# Language validation evidence

Migration `V26__language_validation_features.sql` adds the document-scoped
features `language.detected`, `language.confidence` and `language.agreement`.

The analytics worker writes the prediction and confidence returned by the
local fastText runtime together with `value_status` and `warning`. When the
prediction is valid, `language.agreement` records whether it matches the
catalog language. This is evidence only: the worker never changes catalog
metadata automatically. Missing model artifacts remain explicit
`MODEL_UNAVAILABLE` values.

The migration is additive and idempotent (`ON CONFLICT DO NOTHING`). Existing
feature and run records are not rewritten.
