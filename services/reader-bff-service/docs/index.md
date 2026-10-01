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
