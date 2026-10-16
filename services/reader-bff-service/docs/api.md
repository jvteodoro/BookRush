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

O frontend concentra essas rotas em `frontend/src/app/apiRoutes.ts`. Assim,
uma mudança de ownership ou prefixo é feita no mapa, sem espalhar URLs pelos
componentes. O `readiumBridge` também remapeia os `href` de conteúdo EPUB
para o BFF, preservando HTML, imagens, CSS e demais recursos binários.
