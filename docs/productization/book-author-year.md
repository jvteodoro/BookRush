# Autor e ano dos livros

O catálogo é a fonte dos metadados bibliográficos. GET `/api/v1/books` (incluindo busca) e GET `/api/v1/books/{bookId}`, além dos aliases internos, retornam `authors` (nomes com papel AUTHOR, na ordem de crédito) e `firstPublicationYear` (ano da primeira publicação da obra). Tradutores, editores e ilustradores não são apresentados como autores. O ano não é inferido da edição, data de ingestão ou vida do autor.

O feed de recomendações e o book-content/reader BFF preservam esses campos do catálogo. O frontend apresenta os autores e o ano no feed, busca, biblioteca, recomendações, resultado de “Estou com sorte” e detalhes. Nomes vazios e repetidos são omitidos na apresentação; metadados ausentes mostram “Autor não informado” e, nos detalhes, “Não informado” para o ano. Cartões omitem o ano quando ausente.

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
