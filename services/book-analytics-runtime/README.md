# BookRush Analytics Runtime

Python 3.12 bounded worker for pinned spaCy PT/EN NLP. It makes no network requests and returns `MODEL_UNAVAILABLE` until an operator has prepared and installed the matching locked wheel. Java remains the owner of `analytics`; this runtime only performs bounded inference over an internal API.
