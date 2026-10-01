# Runbook

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

O worker determinístico é ativado no Compose por `ANALYTICS_WORKER_ENABLED=true`.
Ele requer `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY` e `STORAGE_SECRET_KEY`, além
de bucket/key/hash/size registrados no catálogo. Para uma execução local sem
object storage, mantenha a variável em `false`; jobs permanecerão `PENDING`.

IDs de livro, hashes, operation keys e texto nunca são tags. Para investigar um
job, consulte primeiro `GET /api/admin/v1/analytics/jobs/{id}` e depois
`/items`; use `resume` somente após corrigir o erro persistido. O cache/provider
LLM desabilitado não gera chamadas externas.

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
até `ANALYTICS_NLI_MAX_EXCERPTS` candidatos por execução recebem as hipóteses
versionadas de narrativa e emoção. Cada observação grava entailment, neutral,
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
