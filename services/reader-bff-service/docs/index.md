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

Library operations use `GET /api/v1/reader/library` and
`PUT`/`DELETE /api/v1/reader/library/{bookId}`. The gateway routes the exact
listing path and the `/api/v1/reader/library/` prefix to this BFF. Mutations
forward the product bearer to reader-state, where the principal owns the saved
entry; a request body or query parameter cannot select another reader. Saves
and removals are idempotent. Invalid UUIDs return `400`, missing or invalid
tokens return `401`, and upstream failures return `502`. CORS permits the
configured reader origin for GET/PUT/DELETE and rejects untrusted origins.
The book ID in the path is sufficient to save: PUT accepts an empty body,
while remaining compatible with existing JSON clients.
The metadata E2E in Jenkins covers save/list/remove, repeated mutations, two
readers, persistence after a service restart and saving without marking seen.

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

## Marca??es de leitura

GET/POST `/api/v1/reader/books/{bookId}/bookmarks` encaminham o bearer e o
locator Readium ao reader-state, propriet?rio do dado. GET retorna locator
serializado como string JSON; POST retorna a identidade persistida, inclusive
em salvamentos repetidos. O contrato OpenAPI descreve sucesso e erros. Os
status 4xx do servi?o s?o preservados e falhas de depend?ncia viram 502.
As origens CORS e a rota p?blica existentes continuam sendo usadas.
Consulte a se??o de marca??es do reader-state para migration V10, compatibilidade
com clientes antigos e valida??o atrav?s do gateway.

## Retomada autom?tica

GET/PUT `/api/v1/reader/books/{bookId}/progress` encaminham o bearer e o locator
completo ao reader-state. GET retorna zero ou uma linha com locator JSON string;
PUT aceita percent de 0 a 100 e locator opcional para compatibilidade legada.
O frontend usa essa posi??o para retomar sem uma marca??o manual. Servi?o dono,
roteamento, audience e origens CORS permanecem os mesmos; 4xx s?o preservados e
falhas upstream resultam em 502. Consulte o OpenAPI e a se??o de progresso do
reader-state para contrato e valida??o p?blica.

## Editar nomes e excluir marca??es

PUT/DELETE `/api/v1/reader/books/{bookId}/bookmarks/{bookmarkId}` encaminham o
bearer para reader-state, que valida a propriedade por principal e livro.
PUT envia apenas name e retorna 200 com a marca??o; DELETE retorna 204 vazio.
Os erros 400/401/403/404 s?o preservados; indisponibilidade resulta em 502.
As origens expl?citas e os m?todos PUT/DELETE j? fazem parte do CORS do BFF.
O POST existente tamb?m encaminha name e mant?m compatibilidade sem esse campo.
O OpenAPI descreve os limites do nome e as respostas. ReaderBookmarkTest cobre
encaminhamento, bearer, resposta vazia da exclus?o e preserva??o de 404.

## Inicialização e healthcheck

O construtor de produção do controller é selecionado explicitamente pelo
Spring para manter as URLs upstream injetadas no container. O endpoint
`/actuator/health` permanece disponível para o healthcheck do Compose.
