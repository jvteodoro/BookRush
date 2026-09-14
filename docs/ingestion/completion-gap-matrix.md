# Matriz de lacunas — estado real da ingestão e próximos incrementos

Esta matriz confronta o código executável, as migrations, os testes e o
relatório de validação. Um item só é concluído quando há efeito observável no
PostgreSQL, no SeaweedFS e nos endpoints reais. O vertical Gutenberg já foi
validado; os itens abaixo descrevem o que foi comprovado e o que continua como
evolução futura.

| Requisito | Evidência atual | Limitação verdadeira | Próximo passo |
|---|---|---|---|
| Disparar importação | `IngestionAdminController`, `IngestionJobService` e dispatcher persistem jobs e itens | Novas fontes ainda não usam todo o fluxo | Evoluir por fonte |
| Fila e leases | V7/V9, `LeaseCoordinator`, fencing e tentativas persistentes | Cancelamento e métricas de throughput podem evoluir | Instrumentar analytics |
| Aquisição Gutenberg | Parser RDF, downloader, RAW privado e fixture | Prova online depende de rede | Manter prova controlada |
| Catálogo canônico | Adapter HTTP, comando idempotente, obra/edição/créditos/ID, subjects e provenance por campo | Aplicação de candidatos Wikidata ao catálogo ainda é uma etapa explícita de revisão | Consumir candidatos aprovados |
| Asset SOURCE | Reserva, upload S3, confirmação, hash e `books-source` | Reconciliação de órfãos é operacional | Integrar fontes adicionais |
| Processamento | TXT normalizado, `chapters.json`, `book_chapter`, excerpts/features/ranking offline e linhagem | Worker persistente ainda precisa conectar todos os analyzers a jobs de produção | Integrar worker |
| Recuperação | Retry persistente, resume, reconciliation e cleanup | Retenção configurável pode evoluir | Medir e documentar |
| Segurança | OIDC condicional, papéis administrativos e token canônico | Provisionamento é responsabilidade do ambiente | Reutilizar contratos no analytics |
| Gateway | Traefik e Nginx públicos configurados | Rotas futuras precisam ser registradas | Atualizar catálogo Backstage |
| Jenkins | Validação de documentação e deploy | Importação automática é opt-in | Adicionar jobs analytics explícitos |
| Prova | Fixture bibliográfica e analytics offline, lote parcial, dry-run, restart, Gutenberg 1342 e benchmark sintético | Benchmark é baseline de harness, não SLA; storage remoto depende de ambiente | Repetir com volume operacional |

## Estado comprovado do vertical Gutenberg

O relatório [`validation-report.md`](validation-report.md) registra job
persistente, replay idempotente, criação de obra/edição, asset SOURCE, texto
normalizado, `chapters.json`, projeção de capítulos, lineage, recuperação,
dry-run, lote parcial e verificação de hashes no S3/SeaweedFS. Portanto, esses
itens não devem ser reimplementados por causa de uma matriz antiga.

## Contratos preservados

O catálogo continua dono do schema `catalog`; migrations V1–V13 são imutáveis.
O fluxo usa `books-raw`, `books-source` e `books-processing`, mantém I/O fora
de transações abertas e referencia sempre a versão física exata do conteúdo.
Analytics será aditivo e terá ownership próprio.

## Estado analytics da fase atual

`book-analytics-service` já possui migrations próprias até V5, worker persistente
determinístico (ativado por `ANALYTICS_WORKER_ENABLED`), geração de
candidates/features/ranking determinísticos, providers local/LLM desabilitados
com segurança, APIs administrativas idempotentes, leitura interna paginada,
métricas e scripts offline (`scripts/test-analytics-e2e.sh` e
`scripts/benchmark-analytics.sh`). Embeddings, emoção e LLM continuam providers
opcionais, desligados por padrão, e só devem ser habilitados após registrar
artefato, versão e checksum.
