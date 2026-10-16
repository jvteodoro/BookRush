# API BFF

`GET /api/v1/reader/feed` compõe o feed do recommendation-service e preserva request, impression, model version e rank.

Rotas autenticadas adicionais:

- `GET /api/v1/reader/recommendations`
- `GET /api/v1/reader/library`
- `GET /api/v1/reader/recent`
- `GET /api/v1/reader/streak`
- `GET /api/v1/reader/books/{bookId}/progress`
- `GET /api/v1/reader/books/{bookId}/bookmarks`
- `PUT|DELETE /api/v1/reader/library/{bookId}`
- `PUT /api/v1/reader/books/{bookId}/progress`
- `POST /api/v1/reader/books/{bookId}/bookmarks`
- `POST /api/v1/reader/books/{bookId}/opened`
- `GET /api/v1/reader/books/{bookId}`
- `GET /api/v1/reader/books/{bookId}/publication.json`
- `GET /api/v1/reader/books/{bookId}/chapters`
- `GET /api/v1/reader/books/{bookId}/chapters/{chapterId}/content`
- `GET /api/v1/reader/books/{bookId}/resources/{resource}`
- `POST /api/v1/reader/recommendations/impressions/{impressionId}/viewable`

O BFF repassa `Authorization: Bearer` ao serviço dono. Falhas de dependência
retornam `502 Bad Gateway` para que o cliente diferencie indisponibilidade de
uma resposta vazia válida.

As chamadas de navegador aceitam somente as origens configuradas em
`BOOKRUSH_CORS_ALLOWED_ORIGINS`. O preflight `OPTIONS` é permitido sem bearer,
mas todas as operações de negócio continuam exigindo um JWT válido com a
audience e o scope do produto. A configuração inclui `Authorization`,
`Content-Type`, `Idempotency-Key` e `X-Admin-Client` para que o browser não
transforme uma rejeição de CORS em um erro de rede genérico.

O frontend concentra essas rotas em `frontend/src/app/apiRoutes.ts`. Assim,
uma mudança de ownership ou prefixo é feita no mapa, sem espalhar URLs pelos
componentes. O `readiumBridge` também remapeia os `href` de conteúdo EPUB
para o BFF, preservando HTML, imagens, CSS e demais recursos binários.
