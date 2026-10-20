# reader-bff-service

Reader composition gateway. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Consulte [API](api.md) para a composição do feed.

The recommendation upstream is configured as `bookrush.recommendation-url` and
injected with `RECOMMENDATION_URL` in Compose. Keeping this key inside the
application namespace avoids Spring treating the placeholder as a literal
property name during startup.

The feed endpoint requires the caller principal and forwards the bearer token
to the recommendation service. A request is therefore ledgered under the
authenticated subject rather than an anonymous shared identity.

The BFF also exposes authenticated composition routes for recommendations,
library, recent books, streak, progress, bookmarks, opened events,
recommendation impressions, publication manifests and EPUB resources. It
forwards the caller's bearer token to the owning service and returns `502` when
an upstream dependency is unavailable; it never falls back to anonymous or
mock data. Upstream URLs are configured with `READER_STATE_URL` and
`BOOK_CONTENT_URL`. In the Compose topology, `book-content-service` listens
on port `8093`; the BFF default and Compose override must remain aligned with
that port.
