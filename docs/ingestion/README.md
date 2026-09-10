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
