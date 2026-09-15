# Consulta do catálogo pelo Backstage e pelos serviços

O `catalog-service` é a fonte canônica das obras, edições, autores e assets.
Ingestão, analytics e outras aplicações devem consultar seus dados por HTTP;
conexões diretas ao schema PostgreSQL `catalog` são reservadas ao próprio
serviço. Isso mantém autorização, evolução de schema e proveniência em um único
owner.

## APIs de consulta

No componente `catalog-service`, abra a API `catalog-api` e a documentação
`API` no Backstage. As consultas internas disponíveis são:

```text
GET /api/internal/v1/catalog/books?q=<parte-do-título>&page=0&size=50
GET /api/internal/v1/catalog/books/{bookId}
```

O token técnico precisa ter `bookrush.catalog.read` (ou o scope de escrita já
usado por integrações legadas). A resposta é paginada, ordenada pelo título
canônico e contém apenas metadados da obra. Assets, conteúdo textual e URLs
pré-assinadas permanecem protegidos por suas APIs e políticas próprias.

## Uso por um consumidor

Configure a URL interna do catálogo e injete um token de client credentials no
serviço consumidor. Nunca coloque o token no frontend, em um README ou em uma
query string:

```bash
curl -H "Authorization: Bearer $CATALOG_ACCESS_TOKEN" \
  "$CATALOG_BASE_URL/api/internal/v1/catalog/books?size=50"
```

Para percorrer todo o catálogo, incremente `page` até `last=true`. Para uma
obra específica, persista o UUID retornado e use a rota por ID. O endpoint não
inicia ingestão, não baixa assets e não altera o catálogo, portanto é seguro
para reprocessamentos e consultas de analytics.
