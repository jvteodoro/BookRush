# API

Mantém obras, edições, autores, identificadores e assets; autoriza distribuição por URL S3 temporária.

Tokens administrativos de assets e token interno canônico; leitura pública somente nas rotas explicitamente permitidas.

As rotas administrativas de assets (`/api/admin/**`) aceitam preflight CORS
somente das origens configuradas em `CATALOG_CORS_ALLOWED_ORIGINS`. Isso permite
que Swagger/Backstage enviem o bearer sem transformar um preflight recusado em
`TypeError: NetworkError` no navegador.

As operações `GET` de assets exigem `bookrush.catalog.read` (ou uma autoridade
administrativa de assets). Operações de alteração continuam exigindo
`bookrush.assets.write`; portanto, o cliente de documentação pode consultar
assets sem receber permissão de escrita.

O endpoint `GET /api/v1/books/{bookId}/thumbnail` exige o bearer do usuário do
realm de produto `bookrush`. Ele redireciona para uma URL temporária assinada
de um thumbnail aprovado. O frontend deve buscar a imagem com `Authorization`
e usar uma URL de `Blob`; uma tag `<img src="...">` não envia bearer
automaticamente.
O bearer precisa conter o scope `bookrush.catalog.read`; a cadeia de segurança
da rota converte explicitamente esse claim em `SCOPE_bookrush.catalog.read`
antes de avaliar a autorização. Tokens emitidos antes da concessão do scope
devem ser renovados com novo login.

O contrato machine-readable está em api/openapi.yaml, exportado de Springdoc. API Catalog o exibe; endpoints não são duplicados nesta página. Atualize o arquivo a partir da mesma revisão do serviço usando backstage/scripts/export-openapi.py. Breaking changes requerem revisão de consumidores; erros HTTP diferenciam autenticação, validação e indisponibilidade.

## Consultar livros por HTTP

### Busca paginada

`GET /api/v1/books?q=franken&page=0&size=20` executa a pesquisa no catálogo.
O ranking textual e o `count` usam a mesma expressão imutável indexada pela V16;
filtros `ILIKE` continuam cobrindo títulos, autores e assuntos quando a busca
textual não encontra todos os resultados.
`q` procura título canônico, título original, descrição, autor e assunto; os
resultados são ordenados por relevância textual e depois por título. `size` é
limitado a 100. A aba Buscar usa esta API diretamente e solicita somente a
página atual.

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
temporária. O serviço valida issuer e audience do token do realm de produto;
ausência ou rejeição do bearer retorna `401`, e ausência de capa retorna `404`.
A listagem de livros retorna `thumbnailUrl` apontando para essa rota.
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
