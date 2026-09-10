# Relatório de validação da ingestão Gutenberg

## Evidências executadas

- `mvn -q test` em containers Maven para `book-ingestion-service` e
  `catalog-service`: passou.
- Compose descartável com PostgreSQL, Redis, SeaweedFS, catálogo e ingestão:
  Flyway V1–V12 aplicado; healthchecks saudáveis.
- Fixture offline autenticada: job `8a95a181-2402-4656-ac4c-3b02854e87ae`
  terminou `COMPLETED`; criou obra/edição, EPUB SOURCE, TXT normalizado e
  `chapters.json`; a tabela de processamento registrou a linhagem.
- `head-object` via AWS CLI no SeaweedFS confirmou 1013 bytes, MIME EPUB e
  SHA-256 no metadata.
- Replay idempotente retornou HTTP 200 e não criou novo registro.
- Dry-run autenticado em produção retornou `COMPLETED`; o item ficou
  `SKIPPED/DRY_RUN` e não houve criação de `book`, `book_asset_version` ou
  `book_chapter` (job `f57702c3-3845-407b-b57d-173440e3f157`). O worker agora
  despacha jobs após commit, recupera `PENDING/RUNNING` no restart e `resume`
  dispara uma nova execução após o commit.
- Lote `1342` + `999999` terminou `COMPLETED_WITH_ERRORS`, preservando o item
  válido e registrando o 404 do item inexistente.
- Prova online do Gutenberg 1342: job
  `a4acfcaa-f451-47c9-b1da-ad31b95158bc` terminou `COMPLETED`; o banco contém
  uma obra, uma edição, três assets/versões (EPUB source, texto normalizado e
  `chapters.json`) e o capítulo `DOCUMENT`. A projeção usa o header interno
  `X-Canonical-Service-Token`, além do bearer compatível, e foi validada entre
  os containers.
- `git diff --check` passou e os Jenkinsfiles permanecem idênticos.

## Limitações explícitas

- O Jenkins remoto não foi disparado nesta sessão; as imagens foram construídas
  localmente com Docker e os containers de catálogo/ingestão foram recriados e
  ficaram saudáveis. A pipeline foi validada por
  `bash -n` e comparação dos dois arquivos. O disparo deve ser feito pelo
  operador após publicar a branch.
- O teste Testcontainers que exigiria montar o socket Docker não foi executado
  por segurança. O mesmo cenário foi validado pelo Compose descartável sem
  compartilhar o socket.
- Não foi executado benchmark de 100 mil registros; o fluxo foi validado com
  fixture unitária, lote parcial e uma importação online limitada.

O teste controlado alterou deliberadamente o banco persistente de desenvolvimento
no servidor para registrar Gutenberg 1342. Nenhum segredo foi versionado; os
tokens internos usados pelos containers permanecem somente no `.env` local.
