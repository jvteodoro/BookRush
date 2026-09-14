# Microserviço de ingestão

Este diretório documenta a implantação do microserviço de ingestão definido em
`ESPECIFICACAO_MICROSSERVICO_INGESTAO_LIVROS.md`. A execução será Gutenberg-first:
fontes são congeladas em snapshots, preservadas em RAW privado e só os registros
selecionados são aplicados ao catálogo canônico.

## Decisões iniciais

- O `catalog-service` continua dono das entidades `book`, `author`, `edition`,
  `external_identifier`, `book_asset` e de suas versões físicas.
- O novo serviço é dono da aquisição, snapshots, staging, jobs e decisões de
  ingestão. Ele não cria uma segunda cópia dessas tabelas.
- A integração canônica será feita por `CanonicalCatalogPort`, com contrato HTTP
  idempotente e uma única implementação executável. A decisão e os limites estão
  em [ADR-001](adr/001-canonical-ownership.md).
- A ingestão não inicia automaticamente no boot. Scheduler e fontes externas
  permanecem desligados por padrão.

Documentos futuros do serviço ficam nesta árvore: arquitetura, modelo, fontes,
configuração, API, runbook, testes, segurança e relatório final. Os contratos
devem sempre refletir o código e as migrations realmente aplicadas.

O procedimento operacional está em [runbook.md](runbook.md).

As evidências e limitações da validação estão em
[validation-report.md](validation-report.md).

A política compartilhada de falhas está em `com.bookrush.ingestion.retry`:
timeouts/408/429/5xx usam até cinco tentativas com jitter persistido, 401/403
pausam, 404/410 registram ausência e erros de formato vão para quarentena. O
worker deve persistir `next_attempt_at`; ele não deve dormir por minutos.

## Wikidata

O enriquecimento Wikidata segue [ADR-009](adr/009-wikidata-enrichment.md):
snapshot/cache verificável, sem loop por livro, com QIDs e propriedades limitadas
registrados por `field_provenance`. A fonte não altera direitos nem remove
entidades canônicas sozinha.

### Subjects bibliográficos

O `GutenbergImportService` preserva cada subject do RDF como atribuição
idempotente no `catalog-service`, usando `scheme=GUTENBERG`, a `source_record`
correspondente e o método `SOURCE_METADATA`. O catálogo normaliza apenas para
matching (`NFKC`, espaços e minúsculas), mantendo o termo original em
`catalog.subject.canonical_name`. A repetição do job retorna `NOOP` e não cria
uma nova atribuição.

Para Open Library, `OpenLibraryStageJob` mantém os subjects no snapshot DuckDB;
`OpenLibrarySubjectExtractor` extrai termos textuais e objetos `{name}` sem
consultas HTTP por livro. A canonização posterior deve enviar esses termos ao
mesmo endpoint com `scheme=OPEN_LIBRARY`, vinculando o `source_record` da linha
staged. Assim, vocabulários diferentes podem coexistir sem transformar um
subject externo em gênero universal.

### Enriquecimento Wikidata

O leitor `WikidataSnapshotReader` aceita somente um snapshot JSONL local,
verifica SHA-256 antes de ler, limita bytes/registros e valida QIDs. O
`WikidataEnrichment` transforma apenas propriedades aprovadas no ADR-009 em
candidatos com `qid`, propriedade, campo, confiança, linha e hash da evidência.
Esses candidatos são dados de staging: a aplicação no catálogo deve passar pela
API canônica e registrar `field_provenance`. Não há chamadas HTTP por livro,
download implícito ou deleção por redirect.
