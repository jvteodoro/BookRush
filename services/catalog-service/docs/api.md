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
