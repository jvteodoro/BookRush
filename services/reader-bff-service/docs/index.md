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

With analytics ranking enabled, the recommendation service returns only books
with an eligible analytics score and persisted excerpt. An empty `items` array
is a valid result while the analytics batch is still being processed; it is not
filled with catalog metadata or placeholder excerpts. The BFF allows up to 15
seconds for this bounded batch so normal load does not become a spurious 502.

The BFF also exposes authenticated composition routes for recommendations,
library, recent books, streak, progress, bookmarks, opened events,
recommendation impressions, publication manifests and EPUB resources. It
forwards the caller's bearer token to the owning service and returns `502` when
an upstream dependency is unavailable; it never falls back to anonymous or
mock data. Upstream URLs are configured with `READER_STATE_URL` and
`BOOK_CONTENT_URL`. In the Compose topology, `book-content-service` listens
on port `8093`; the BFF default and Compose override must remain aligned with
that port.

## Estado de livros vistos

`GET /api/v1/reader/books/seen?bookIds=<uuid>,<uuid>` encaminha uma lista validada de
1–100 UUIDs e o bearer original ao reader-state, mantendo identidade no serviço
dono do dado. A rota existente POST `/books/{bookId}/opened` agora registra
carregamento concluído e a marca persistente, sem alterar a API de progresso.
Produto bookrush, audience account, sem permissão adicional. Respostas: 200
com items, 400 para lote inválido, 401 para token ausente/inválido, 403 por origem
CORS indevida e 502 se o reader-state estiver indisponível.
Não há retorno silencioso de lista vazia em caso de falha da dependência.
O gateway Traefik existente já roteia `/api/v1/reader` para este BFF.

## Inicialização e healthcheck

O construtor de produção do controller é selecionado explicitamente pelo
Spring para manter as URLs upstream injetadas no container. O endpoint
`/actuator/health` permanece disponível para o healthcheck do Compose.
