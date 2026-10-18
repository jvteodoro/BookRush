# Runbook

O desenho e as decisões de capacidade do job estão consolidados em
[Arquitetura de processamento em lote](../../../../docs/analytics/batch-processing-architecture.md).

Health: `/actuator/health`. OpenAPI: `/v3/api-docs` e `/swagger-ui/index.html`.
Falhas de migration devem ser tratadas no banco do serviço; não use
`ddl-auto=update` nem edite migrations aplicadas. Uma análise falha sem apagar
observações de execuções anteriores.

Jobs administrativos usam `operationKey` única. Repetir a mesma operação retorna
o job existente; itens permanecem persistidos para retomada. Cancelamento é
cooperativo em `POST /api/admin/v1/analytics/jobs/{id}/cancel`.

LLM teacher é opcional e permanece desligado por padrão. O cache é identificado
por hash do texto + versão do prompt + template; labels devem ser validados e
registrar modelo, tokens, timestamp e provenance. Falha do provider não pode
impedir features determinísticas.

Labels teacher aceitam somente `curiosity_gap`, `self_containment`,
`cliffhanger`, `spoiler_risk` e `quotability`, sempre em `[0,1]`. Respostas com
labels desconhecidos ou fora do domínio são rejeitadas e não viram features.

Operações teacher/editoriais recebem somente top-K candidatos ordenados pelo
score baseline, com limite explícito. O pipeline barato continua sendo
funcional quando K=0 ou o provider remoto está desligado.

O dataset teacher/student usa JSONL com `excerpt_id`, hash do texto, label,
confidence, modelo teacher, versão do prompt e timestamp. A exportação deve ser
feita para `books-ml` e nunca altera o catálogo canônico.

## Métricas e diagnóstico

O actuator expõe métricas de baixa cardinalidade em `/actuator/metrics` e
Prometheus quando habilitado. Os nomes principais são:

- `bookrush.analytics.jobs{outcome=created|replayed|conflict}`;
- `bookrush.analytics.excerpts{operation=listed}`;
- `bookrush.analytics.excerpts{operation=generated}`;
- `bookrush.analytics.failures{stage=analysis}`;
- `bookrush.analytics.analyzer.duration`.
- `bookrush.analytics.phase.duration{phase=...}` com percentis P50/P90/P95/P99;
- `bookrush.analytics.phase.completions{phase=...}` e
  `bookrush.analytics.phase.failures{phase=...}`.

As durações históricas também são persistidas em
`analytics.analysis_phase_timing`, nas fases `ITEM_TOTAL`, `LOAD_METADATA`,
`READ_ASSET`, `CHAPTER_DISCOVERY`, `LANGUAGE_VALIDATION`,
`EXCERPT_SELECTION`, `EXCERPT_PERSISTENCE`, `EXCERPT_EMBEDDINGS`, `FEATURES`
e `SCOPE_EMBEDDINGS`. O endpoint administrativo
`GET /api/admin/v1/content-analytics/phase-timings?jobId=<uuid>` retorna média,
mínimo, P50, P90, P95, P99 e máximo por fase/estado. Use Prometheus para
alertas e séries temporais; use o endpoint para comparar batches históricos.

O worker determinístico é ativado no Compose por `ANALYTICS_WORKER_ENABLED=true`.
Ele requer `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY` e `STORAGE_SECRET_KEY`, além
de bucket/key/hash/size registrados no catálogo. Para uma execução local sem
object storage, mantenha a variável em `false`; jobs permanecerão `PENDING`.

O worker usa uma lease lógica para evitar que uma interrupção deixe itens
permanentemente em `RUNNING`. Itens de jobs ativos, não cancelados, sem
atualização por `ANALYTICS_WORKER_STALE_AFTER_SECONDS` (900 segundos por
padrão) são devolvidos a `PENDING` antes do próximo claim. O limite deve ser
maior que o pior tempo esperado de uma análise; ele não remove itens, jobs,
execuções ou objetos do storage. Depois de um restart, acompanhe o endpoint de
itens do job e confirme que os itens recuperados voltaram a `COMPLETED` ou
`FAILED` com uma nova tentativa.

Falhas transitórias também retornam automaticamente a `PENDING`: o worker usa
até `ANALYTICS_WORKER_MAX_ATTEMPTS` (3 por padrão) e backoff progressivo baseado
em `ANALYTICS_WORKER_RETRY_DELAY_SECONDS` (30 segundos por padrão). Erros de
integridade, asset inexistente e argumentos inválidos continuam terminais e
viram `FAILED`; não são repetidos indefinidamente. O `attempt_count` persistido
impede que um item seja processado além do limite, enquanto o job continua
`RUNNING` quando ainda existem itens pendentes.

A persistência de features de cada excerpt usa `JdbcTemplate.batchUpdate`,
mantendo a unidade idempotente mas evitando um commit síncrono por feature.
Isso reduz a pressão sobre `WALSync`; `WALSync` prolongado deve ser investigado
com `pg_stat_activity`, `pg_stat_wal` e saúde do volume, não resolvido
desativando `fsync` ou `synchronous_commit` globalmente.

IDs de livro, hashes, operation keys e texto nunca são tags. Para investigar um
job, consulte primeiro `GET /api/admin/v1/analytics/jobs/{id}` e depois
`/items`; use `resume` somente após corrigir o erro persistido. O cache/provider
LLM desabilitado não gera chamadas externas.

Uma falha de item não encerra o lote enquanto existirem itens `PENDING` ou
`RUNNING`: o worker reconcilia os contadores e mantém o job `RUNNING`, mesmo
após reinício do container. O lote só termina como `COMPLETED` ou
`COMPLETED_WITH_ERRORS` quando não restam itens transitórios. Se um job terminal
ainda tiver itens pendentes, trate-o como estado legado e use `resume` após
verificar os erros dos itens; não crie um segundo job para a mesma
`operationKey`.

## Benchmark offline

Execute `scripts/benchmark-analytics.sh [books]` from the repository. O harness
usa seed fixa 1337, texto sintético local, mede livros/excerpts por segundo,
bytes UTF-8 e RSS do processo; embeddings permanecem explicitamente desabilitados
(`0`) e não representam throughput de modelo. O resultado abaixo foi observado
em 2026-09-14 nesta máquina, com 1.000 livros: `12.000` excerpts, `0,014 s`,
`72.785,88 livros/s`, `873.430,59 excerpts/s`, `3.079.000` bytes UTF-8 e
`9.608 KiB` de RSS. É baseline de harness, não SLA de produção; repita após
alterar o algoritmo ou o hardware.

## Preparação explícita de modelos

Antes de habilitar embeddings/NLI, prepare o cache com `make analytics-models-fetch`
e valide os SHA-256. O serviço não baixa modelos no startup. Em modo offline,
`ANALYTICS_OFFLINE=true`, artefato ausente resulta em `MODEL_UNAVAILABLE` para o
estágio dependente. Nenhum bulk é iniciado automaticamente; siga os gates do
`VALIDATION_PLAN_V1.md`.

## Fases 1–8 habilitadas

O serviço exige bearer OIDC do realm administrativo quando
`ANALYTICS_SECURITY_ENABLED=true`. O token deve conter a audience
`bookrush-analytics` e o scope `bookrush.analytics`; a configuração declarativa
do scope fica em `infrastructure/keycloak/bookrush-platform-realm.json` e é
aplicada por `infrastructure/keycloak/reconcile.sh`. Tokens de produto ou de
outro client não autorizam a API.

`ANALYTICS_SCOPE_EMBEDDINGS_ENABLED=true` gera embeddings BGE-M3 de excerpts,
capítulos e documentos. Capítulos e documentos são divididos em chunks de
codepoints e agregados por média ponderada L2, preservando o texto completo e a
versão física de entrada. Os vetores são objetos privados em `books-ml`, com
metadados e hashes nas tabelas `chapter_embedding` e `document_embedding`.

`ANALYTICS_NLI_ENABLED=false` mantém NLI desligado por padrão. Ao habilitar,
até `ANALYTICS_NLI_MAX_EXCERPTS` candidatos por execução (limitado a 50) recebem
as hipóteses versionadas de narrativa e emoção. A seleção é determinística e
combina qualidade estrutural/lexical com embeddings BGE, aplicando MMR para
evitar que um único capítulo ou grupo de trechos semelhantes domine o lote.
Cada observação grava entailment, neutral,
contradiction, support e confidence como scores de modelo; nenhum deles é uma
probabilidade comportamental. A migration V11 cria as definições tipadas.

O runtime também expõe `/v1/language`, usando o artefato fastText `lid.176.bin`
somente do cache local. O worker aceita o idioma detectado apenas para `en` ou
`pt` com confiança mínima de 0,60; caso contrário mantém o idioma canônico ou
marca a análise como não suportada. O timeout HTTP é configurado por
`ANALYTICS_RUNTIME_TIMEOUT_SECONDS` (60 segundos por padrão), podendo ser
aumentado para o embedding completo de documentos em CPU.

O comando `scripts/publish-corpus-artifact.py` gera o artifact de frequência,
calcula SHA-256, publica no bucket privado `books-ml` e imprime os metadados para
registro em `analytics.corpus_frequency_model`. A normalização de estilo é
registrada com ordem de features, médias, desvios padrão e checksum; os valores
ausentes continuam sendo `UNSUPPORTED`, nunca zero.

Para materializar o conjunto semântico real, prepare o cache BGE-M3 validado e
execute `python3 scripts/materialize-semantic-prototypes.py --endpoint
http://book-analytics-runtime:8092 --output
services/book-analytics-service/config/semantic-prototypes-v1.artifact.json`.
O comando exige o modelo `bge-m3-dense-v1`, dimensão 1024, verifica os hashes de
entrada, normaliza cada embedding, calcula centroides EN/PT/multilíngues e grava
um checksum SHA-256 canônico. O validador de contratos rejeita artefatos ausentes,
alterados ou não normalizados; o serviço nunca baixa modelos durante o boot.

## Validação dos artefatos reais

O E2E determinístico não prova que o cache de modelos está utilizável. Execute
`bash scripts/test-analytics-models.sh` com o volume de modelos montado em
somente leitura. O harness constrói a imagem pinned do runtime e verifica
respostas `VALID` reais de fastText EN/PT, spaCy EN/PT, BGE-M3 (1024 dimensões
normalizadas) e mDeBERTa NLI (scores que somam 1). Ele não baixa modelos, não
altera o volume, não inicia jobs de analytics e não imprime credenciais.

```bash
ANALYTICS_MODEL_VOLUME=bookrush_analytics_models bash scripts/test-analytics-models.sh
```

Falha nessa validação é NO-GO para os gates do V1. `MODEL_UNAVAILABLE` não pode
ser convertido em valores zero nem usado como evidência para fechar os beads de
bulk-readiness.

## Piloto isolado de capacidade

Para medir o caminho de runtime sem tocar no catálogo, execute:

```bash
ANALYTICS_MODEL_VOLUME=bookrush_analytics_models \
ANALYTICS_PILOT_OUTPUT=/tmp/bookrush-analytics-pilot.json \
bash scripts/run-analytics-pilot.sh
```

O piloto gera pelo menos 500 fixtures determinísticas (seed `1337`), divide-as
igualmente entre EN/PT, executa embeddings em lotes de 32 e verifica amostras
de idioma, spaCy e NLI. O container monta o cache em somente leitura e o
relatório declara explicitamente `writes_to_production=false` e
`quality_gate=NOT_ASSESSED`.

Execução de 2026-10-17: `GO_RUNTIME_CAPACITY`, 500/500 embeddings válidos,
40/40 amostras válidas em fastText, spaCy e NLI, 6.000 candidatos estruturais,
334,358 s e 1,495 livros/s. Esse resultado prova capacidade e integridade dos
adaptadores, mas não é GO de qualidade editorial: ainda são necessários corpus
real, revisão humana, comparação PT/EN e calibração do ranker/classificador.

### Piloto com livros reais do catálogo

`scripts/run-analytics-production-pilot.sh` executa uma amostra somente leitura
dos livros Gutenberg já cadastrados. Consulta apenas IDs, idioma e proveniência
no PostgreSQL, baixa temporariamente a fonte pública correspondente e envia o
texto somente ao runtime offline. O relatório contém contagens e hash do
corpus; texto completo, JSONL e credenciais não entram no repositório.

```bash
ANALYTICS_PILOT_OUTPUT=/tmp/bookrush-analytics-production-pilot.json \
bash scripts/run-analytics-production-pilot.sh
```

O comando não chama ingestão, não cria job e não grava em catálogo ou storage.
Quando o gateway/SeaweedFS estiverem indisponíveis, o resultado deve declarar
que usou o snapshot local de proveniência e a fonte pública Gutenberg; isso
valida o runtime com obras reais, mas não substitui o gate de produção nem a
aprovação editorial.

Execução controlada em 2026-10-17: 8 obras reais (todas EN) do snapshot foram
processadas com janela de 2.000 caracteres e lotes de 4; embeddings 8/8,
language 8/8, linguistic 8/8 e NLI 8/8 válidos em 20,367 s (24,55 obras/s),
`GO_RUNTIME_CAPACITY`, hash
`5a26cfe8c922932997f80783e154abb7f48547cb27bf95b0245a967ad5b98529`.
O gateway público estava em HTTP 502 e o SeaweedFS local parado, portanto a
fonte textual foi Gutenberg público a partir da proveniência do snapshot; o
resultado não é um gate de produção nem cobre qualidade humana, PT ou escrita
de analytics.
