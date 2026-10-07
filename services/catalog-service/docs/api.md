# API

Mantém obras, edições, autores, identificadores e assets; autoriza distribuição por URL S3 temporária.

Tokens administrativos de assets e token interno canônico; leitura pública somente nas rotas explicitamente permitidas.

O contrato machine-readable está em api/openapi.yaml, exportado de Springdoc. API Catalog o exibe; endpoints não são duplicados nesta página. Atualize o arquivo a partir da mesma revisão do serviço usando backstage/scripts/export-openapi.py. Breaking changes requerem revisão de consumidores; erros HTTP diferenciam autenticação, validação e indisponibilidade.

## Consultar livros por HTTP

Consumidores como ingestão e analytics consultam o catálogo pelas rotas internas,
sem credenciais ou conexão direta ao PostgreSQL:

```text
GET /api/internal/v1/catalog/books?q=quixote&page=0&size=50
GET /api/internal/v1/catalog/books/{bookId}
```

O primeiro endpoint retorna `items`, paginação e total de resultados, ordenados
por título canônico. `size` é limitado a 100. O segundo retorna os metadados da
obra ou `404`. Ambos exigem um token de serviço com `bookrush.catalog.read` ou
`bookrush.catalog.write` (o cliente canônico também é aceito para compatibilidade).

Essas rotas expõem somente metadados bibliográficos. Assets e conteúdo físico
continuam sujeitos às rotas de distribuição e às políticas de direitos.

## Thumbnails de capa

`GET /api/v1/books/{bookId}/thumbnail` procura o asset `THUMBNAIL` com papel
`COVER` em estado `ACTIVE` e responde com um redirecionamento para uma URL S3
temporária. A listagem de livros retorna `thumbnailUrl` apontando para essa
rota; clientes devem tratar `404` como ausência de capa e manter seu placeholder.
## Reader chapters

`GET /api/v1/books/{bookId}/reader-assets/chapters` returns the persisted
chapter projection for the book, including the exact text asset version,
Unicode code-point offsets, hierarchy and projection confidence. The endpoint
is read-only and remains owned by the catalog service.

The content service uses the authenticated internal route
`GET /api/v1/books/{bookId}/reader-assets/text-versions/{versionId}/content`
with `X-Content-Service-Token`. It streams the exact `AVAILABLE` normalized
text version after checking the book, asset and object-storage metadata. This
keeps presigned browser capabilities separate from the server-side Readium
chapter proxy and prevents container-local storage URLs from leaking into the
reader flow.

## Projeção interna de capítulos

`POST /api/internal/v1/catalog/processings/chapters` materializa o
`chapters.json` recebido pela ingestão para a versão textual informada. A
operação é idempotente: versões textuais são imutáveis e, quando já existem
capítulos projetados para `textAssetVersionId`, o catálogo responde
`ALREADY_PROJECTED` sem apagar nem recriar as linhas.

Essa regra preserva os IDs estáveis de `catalog.book_chapter`, que podem estar
referenciados por `analytics.excerpt`. Portanto, reimportações e retries não
removem capítulos usados pelo analytics nem falham por violação de chave
estrangeira.
