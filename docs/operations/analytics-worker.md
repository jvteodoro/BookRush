# Worker de analytics: processamento assíncrono e recuperação

O `book-analytics-service` mantém o polling administrativo separado do
processamento pesado. A thread do `@Scheduled` executa apenas recuperação de
leases e `claim` de itens; cada item é delegado a um executor limitado por
`ANALYTICS_WORKER_CONCURRENCY` (1 por padrão). Assim, embeddings ou seleção de
excerpts longos não bloqueiam a recuperação de itens órfãos nem as consultas
administrativas.

## Interrupção e retomada

Cada lote de embeddings é persistido progressivamente no SeaweedFS e no
PostgreSQL. As escritas são idempotentes por hash/chave e `ON CONFLICT DO
NOTHING`: uma parada pode repetir o lote em andamento, mas não perde os lotes
confirmados nem exige recriar o job inteiro.

O scheduler marca fases ainda `RUNNING` de um lease expirado com
`WORKER_RESTART_RECOVERY`, preservando duração e tentativa no histórico. Se o
item ainda tiver tentativas disponíveis, ele volta para `PENDING`; se já tiver
atingido `ANALYTICS_WORKER_MAX_ATTEMPTS`, fica `FAILED` explicitamente, para
não permanecer em uma fila que nunca poderia ser reclamada.

O limite `ANALYTICS_WORKER_STALE_AFTER_SECONDS` deve ser maior que o pior
intervalo esperado sem atualização de progresso. Antes de atualizar a imagem,
consulte o item e seu `progress_updated_at`; depois do restart, confirme no
endpoint administrativo que o item foi concluído, reencaminhado ou marcado
como falha de recuperação.

## Diagnóstico

1. Consulte o job em `/api/admin/v1/content-analytics/jobs/{jobId}`.
2. Consulte os itens e filtre por `RUNNING`, `PENDING` e `FAILED`.
3. Abra o detalhe do item para comparar tentativas e fases; uma fase `FAILED`
   em uma aba histórica não representa necessariamente a tentativa atual.
4. Use `/api/admin/v1/content-analytics/phase-timings?jobId={jobId}` para
   comparar a latência por fase.

Não interrompa um worker ativo apenas por uma duração alta: verifique se
`progress_updated_at` e os logs de lote continuam avançando. Se não houver
avanço além do lease, a recuperação assíncrona evita que esse item bloqueie os
demais.

## Validação em lotes pequenos

Para desenvolvimento, use `scripts/run-analytics-pilot.py --allow-small-batch`
com 8–32 livros. O resultado `GO_SMALL_BATCH_RUNTIME` confirma apenas o
contrato dos adaptadores e a integridade do lote; não substitui o gate de
capacidade de 500 livros. O modo padrão continua exigindo pelo menos 500
livros, evitando que um smoke test seja confundido com evidência operacional.

O scorer supervisionado é opt-in e permanece desativado por padrão. Quando
ativado, o worker valida versão, estado e SHA-256 do artefato antes de gravar
dimensões H/C/A/I/Q e sua proveniência em `analytics.excerpt_rank`.

O ranker heurístico também usa configuração versionada no classpath; a
variável `ANALYTICS_RANKER_CONFIG` só deve apontar para artefatos com estado
`UNVALIDATED` até existir um processo formal de promoção.

A validação fastText é normalizada em `LanguageEvidence` antes da persistência:
predição, confiança, idioma declarado, concordância e warning permanecem
separados. `MODEL_UNAVAILABLE` e outros estados não são convertidos em valores
válidos ou em concordância artificial.
