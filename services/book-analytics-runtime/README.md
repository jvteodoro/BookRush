# BookRush Analytics Runtime

Python 3.12 bounded worker for pinned spaCy PT/EN NLP. It makes no network requests and returns `MODEL_UNAVAILABLE` until an operator has prepared and installed the matching locked wheel. Java remains the owner of `analytics`; this runtime only performs bounded inference over an internal API.

The linguistic endpoint exposes POS ratios, finite-verb density, dependency
depth/distance, entity counts and bounded referential signals. The
`unresolved_reference_proxy` is deliberately only a proxy: it counts pronoun or
demonstrative tokens that occur before any detected named entity in the excerpt.
It must not be described or consumed as coreference resolution. Model loading
remains local-only and all unavailable/error states are explicit.
