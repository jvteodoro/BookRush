# Autor e ano dos livros

O catálogo é a fonte dos metadados bibliográficos. GET `/api/v1/books` (incluindo busca) e GET `/api/v1/books/{bookId}`, além dos aliases internos, retornam `authors` (nomes com papel AUTHOR, na ordem de crédito) e `firstPublicationYear` (ano da primeira publicação da obra). Tradutores, editores e ilustradores não são apresentados como autores. O ano não é inferido da edição, data de ingestão ou vida do autor.

O catálogo também retorna `publicationYear`: prioriza `firstPublicationYear` e, quando este está ausente, usa o menor ano conhecido entre as edições. Isso não transforma o ano da edição em primeira publicação da obra. A consulta de edições é agrupada por página, sem uma consulta por livro. O frontend normaliza esse valor para apresentação como “Ano de publicação”.

O feed de recomendações e o book-content/reader BFF preservam esses campos do catálogo. O frontend apresenta os autores e o ano no feed, busca, biblioteca, recomendações, resultado de “Estou com sorte” e detalhes. Nomes vazios e repetidos são omitidos na apresentação; metadados ausentes mostram “Autor não informado” e, nos detalhes, “Não informado” para o ano. Cartões omitem o ano quando ausente.

## Livros do Gutenberg

A importação lê o ano bibliográfico da declaração de publicação `pgterms:marc260` do RDF e o envia no `publicationYear` do comando canônico, que o armazena em `catalog.edition.publication_year`. A data `dcterms:issued` é o lançamento digital no Gutenberg e nunca serve como fallback. Declarações sem ano explícito, com intervalos, múltiplos anos ou datas incertas permanecem sem ano. O ano extraído integra o hash semântico e os metadados da fonte.

Para livros já importados, depois de implantar **ingestão, catálogo e frontend**, execute novamente a importação dos IDs Gutenberg pela operação administrativa existente, com `reprocess: true`, `processAssets: false`, `dryRun: false` e uma nova chave de idempotência. Isso atualiza os metadados sem baixar novamente EPUBs ou substituir volumes. Apenas repetir a chave antiga retorna o resultado em cache. Exemplo de corpo para `POST /ingestion/api/admin/v1/ingestion/run` (rota interna: `/api/admin/v1/ingestion/run`):

```json
{"source":"GUTENBERG","externalIds":["1342"],"maxItems":1,"reprocess":true,"processAssets":false,"dryRun":false}
```

Use a sessão administrativa autorizada; não há alteração de autenticação, CORS ou migrations nesta correção. Nem todo registro Gutenberg informa a publicação bibliográfica: nesses casos o ano continua ausente, mesmo após o reprocessamento. Não foram executados deploy ou reprocessamento no ambiente de produção.

Validação da correção: 83 testes do frontend, build e lint; 27 testes unitários do catálogo e 6 de integração com PostgreSQL 16; 33 testes da ingestão aprovados (o teste opcional que depende de `/tmp/2641.epub` foi ignorado por ausência desse arquivo). Cobertura adicionada para RDF com ano bibliográfico diferente do lançamento digital, datas ambíguas/ausentes, transporte do ano no comando de ingestão, leitura da edição em lista/busca/detalhe e renderização das recomendações. Chromium confirmou autor e ano de uma fixture local servida pela API; o JAR do catálogo também respondeu corretamente em container Java 21. Contratos de pipeline e change gate passaram. Essas evidências locais não validam login, ingestão ao vivo do Gutenberg ou deploy no gateway público.

A consulta de autores usa uma única consulta por página, ordenada por book_author.position. Não há migration: catalog.author, catalog.book_author e catalog.book.first_publication_year já existem. Autorização, CORS e caminhos do gateway mantêm seus contratos existentes.

Validação local: `npm ci`, `npm run build`, `npm test` e `npm run lint` em frontend; `mvn verify` em services/catalog-service, com as bibliotecas de platform instaladas. Os testes cobrem coautoria, ausência de metadados, lista/detalhe e transporte na busca/biblioteca. Login real, deploy Jenkins e smoke pelo gateway público exigem ambiente e sessão autorizados; resultados locais não comprovam implantação em produção.

## Evidências da entrega

- Frontend: build e lint aprovados; 70 testes passaram.
- Catálogo: 26 testes unitários passaram; nova verificação de lista/detalhe aprovada.
- PostgreSQL 16 descartável: 4 testes JpaCatalogIT passaram, incluindo ordem dos créditos, coautoria e exclusão de tradutor.
- Smoke HTTP da API local retornou authors=[Machado de Assis] e firstPublicationYear=1899 para uma fixture de Dom Casmurro.
- Chromium: cartões renderizaram coautoria, ano e fallback sem inventar data.
- Contratos de pipeline, sintaxe Bash e change gate passaram. OpenAPI YAML e schema de authors conferidos.
- Sem deploy ou validação de sessão real no gateway público nesta entrega.
- Beads bloqueado: bd prime/ready/list não abrem o banco embedded por ausência de `.beads/embeddeddolt/bookrush/.dolt/repo_state.json`. Dados preservados; não houve reinicialização forçada nem sincronização de tarefas.
