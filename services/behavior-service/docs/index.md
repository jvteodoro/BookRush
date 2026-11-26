# behavior-service

Accepted events are written transactionally with a durable outbox. The
maintenance job rebuilds derived aggregates and retention only. Set
`BEHAVIOR_OUTBOX_SINK_URL` to an internal authenticated event sink to enable
delivery; failed deliveries remain pending and increment `attempts` with an
error, rather than being marked as published.

O job executa agregação e retenção na mesma transação. Seu cutoff UTC usa
`OffsetDateTime`, compatível com `timestamptz` no PostgreSQL; não passe `Instant`
diretamente como parâmetro JDBC, pois uma falha de retenção reverte os totais.
Nos agregados diários usados pelo publicador, `likes` conta apenas `BOOK_LIKE`,
sem aumentar quando há `BOOK_UNLIKE`; não é um saldo de curtidas ativas.
Execute `BehaviorMaintenancePostgresIT` com `TEST_DATABASE_URL` em uma base
descartável para validar o job completo, o commit dos totais, a retenção e a
reexecução idempotente. Consulte [as evidências e o fluxo de estatísticas](../../../docs/productization/publisher-book-statistics.md).

Append-only behavioral events and aggregates. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 store idempotent append-only events and rebuildable daily book aggregates.

## Export

`pip install -r scripts/requirements-export.txt` seguido de
`python3 scripts/export-parquet.py --source events.jsonl --cutoff 2026-10-01T00:00:00Z --output behavior.parquet`
gera Parquet comprimido com ordenação determinística e manifesto contendo
schema, cutoff, contagem e SHA-256. O upload S3 deve ser executado pelo job
operacional com credenciais fornecidas em runtime.

V4 adiciona registry de schemas e rejects observáveis para eventos inválidos; rejeições não entram no log canônico.

V5 adiciona `behavior.daily_user_book`, um agregado diário por identidade técnica
e livro. O endpoint autenticado `GET /api/v1/behavior/aggregates/me` retorna apenas
as linhas do `iss/sub` resolvido pelo token atual. A reconstrução em
`POST /api/v1/behavior/aggregates/rebuild` atualiza os agregados globais e por
usuário; `active_seconds` só soma valores inteiros não negativos do evento
`READ_SESSION`, ignorando payloads malformados. Esses dados são métricas de uso,
não uma tabela de perfil ou de biblioteca do usuário.

V9 adiciona `behavior.user_book_signal`, uma projeção reconstruível destinada ao
serving de recomendação. Ela não substitui `daily_user_book`: mantém estado
atual de `liked`/`saved`, contadores de abertura, progresso, conclusão,
`active_seconds`, progresso máximo, exposição/viewability e timestamps por
`identity_subject` e livro. `UNLIKE`/`UNSAVE` substituem o estado anterior;
impressões alimentam exposição/fadiga e não são afinidade positiva. O scheduler
e `POST /api/v1/behavior/aggregates/rebuild` usam a mesma reconstrução. A
projeção só inclui eventos com sujeito e `book_id`, portanto uma impressão sem
livro continua disponível no log bruto, mas não é atribuída artificialmente a
um livro.

As projeções tratam `EXCERPT_IMPRESSION` com `impressionId` como um fato lógico
deduplicado, mesmo quando retries chegam com `eventKey` diferentes. Impressões
sem correlação preservam o `event.id` como fallback; elas não são artificialmente
unidas. O frontend não envia mais um evento paralelo quando o ledger do feed já
confirma a viewability server-side. O teste PostgreSQL cobre a combinação de
reenvio, viewability e idempotência.

`BOOK_OPEN` correlacionado também usa `impressionId` ou `recommendationItemId`
como chave lógica nos agregados; aberturas orgânicas permanecem separadas pelo
ID do evento. O log append-only mantém todas as tentativas para reconciliação.

## API interna de sinais

`POST /api/internal/v1/recommendation/signals` é uma API somente S2S para o
recommendation-service. O corpo recebe o `subject` opaco, uma lista opcional de
até 100 `bookIds` e paginação (`limit` entre 1 e 100, `offset` entre 0 e
10.000). A resposta retorna apenas o read model `user_book_signal`, com
contadores, estado reversível e timestamps; payloads brutos de eventos e PII
não são expostos.

O endpoint valida tokens do realm `bookrush-platform` com audience
`bookrush-behavior` e scope `bookrush.behavior.read`. A autenticação de usuário
do realm `bookrush` continua sendo validada separadamente nas rotas de ingestão
e agregados. O cliente declarativo é
`bookrush-recommendation-behavior-s2s`; seu segredo deve existir somente no
ambiente protegido e não é necessário para o serviço responder.

`GET /api/internal/v1/recommendation/trending?limit=N` expõe a fonte real de
trending do PFV1. Ele usa uma janela de sete dias com decaimento exponencial
sobre opens positivos, saves, likes, sessões/progresso de leitura e conclusões;
`BOOK_UNLIKE`/`BOOK_UNSAVE` não entram como atividade positiva. O score é
bounded em `[0,1]`, versionado como `trending-v1` e não representa probabilidade
calibrada de engajamento.

Os tokens S2S são JWTs `RS256` assinados pelo realm `bookrush-platform`. O
decoder do serviço aceita explicitamente esse algoritmo para não confundir a
chave de assinatura com chaves RSA de criptografia publicadas no mesmo JWKS.
Após uma rotação de chaves do Keycloak, o serviço deve ser recriado ou
reiniciado para recarregar o JWKS antes de validar o smoke S2S.

The ingestion endpoint derives `identity_subject` from the authenticated
principal and ignores identity fields supplied in the event body. For a
validated product JWT, the stored key is the same opaque technical identity
(`issuer` plus `sub`) used by recommendation-service; non-JWT principals keep
their principal name for local and test adapters. Batches are limited to 100
events and payloads to 16 KiB; retries remain idempotent through the unique
`event_key` constraint. This prevents a browser from attributing an event to
another user and keeps behavior signals joinable with personalized serving.

## Correlation with recommendation outcomes

Feed-originated events may include the optional UUID strings
`recommendationItemId` and `impressionId` in their payload. The service validates
these values, derives the subject only from the authenticated token, and stores
the correlation in `behavior.outcome_attribution` for open, progress, session,
completion, social and save outcomes. Events outside a recommendation feed may
omit both fields and remain valid; their correlation columns are `NULL`.
There is intentionally no cross-schema foreign key: recommendation-service
validates impression ownership before emitting viewability, while behavior
enforces subject scoping at ingestion.

`V11__versioned_outcome_attribution.sql` adds `attribution_status`,
`attribution_rule_version` and `attribution_window_seconds`. Rebuilds classify
outcomes as `ATTRIBUTED`, `ORGANIC`, `OUTSIDE_WINDOW` or
`INVALID_REFERENCE`; only an authenticated subject/book/impression match to a
viewable exposure in the seven-day `attribution-v1` window is attributed. The
classification is rebuildable and does not delete historical events.

`V12__attribution_exposure_touch.sql` persists `exposure_event_id` and
`attributed_exposure_at`. The attribution rebuild accepts either
`impressionId` or `recommendationItemId`, chooses the latest eligible viewable
exposure in the seven-day window (timestamp, impression and event ID are stable
tie-breakers), and retains the selected touch for auditability.

### Integração PostgreSQL

`UserBookSignalPostgresIT` usa Testcontainers por padrão. Em hosts cujo cliente
Docker embutido não negocia a API mínima do daemon, o teste aceita um banco
descartável externo por meio de `BOOKRUSH_TEST_POSTGRES_URL`, além de
`BOOKRUSH_TEST_POSTGRES_USER` e `BOOKRUSH_TEST_POSTGRES_PASSWORD`. O banco deve
ser criado somente para o teste e removido ao final; nenhum volume produtivo é
aceito.

## Outbox downstream

Eventos aceitos entram na outbox e são enviados pelo dispatcher para
`BEHAVIOR_OUTBOX_SINK_URL`. O ambiente Compose usa o consumidor interno do
`book-analytics-service` em `/api/internal/v1/behavior/events`; a autenticação
entre os serviços usa `BEHAVIOR_OUTBOX_SINK_TOKEN`, mantido somente no `.env`
protegido. A linha só recebe `published_at` depois de uma resposta 2xx.

## Métricas de publicações

`GET /api/internal/v1/publisher/metrics/books/{bookId}?days=N` é uma rota
somente S2S, autorizada pelo audience `bookrush-behavior` e scope dedicado
`bookrush.publisher.metrics.read`. O período aceita 1–365 dias e lê agregados
diários de impressões, aberturas, leituras, curtidas, compartilhamentos e
comentários da projeção `behavior.daily_book`, atualizada pelo job de
manutenção. O publisher-service valida a propriedade da publicação antes de
chamar a rota. A resposta não inclui identidades de leitores. O segredo da
identidade S2S permanece no ambiente protegido.

O resumo com supressão de amostras pequenas usa a rota separada
`GET /api/internal/v1/publisher/metrics/books/{bookId}/summary?days=N`.
Ela retorna métricas diárias e crescimento em 30 dias; oculta valores quando há
menos de cinco leitores distintos com leitura no período aplicável. O cliente
`PublisherBehaviorMetricsClient` usa essa rota.

## Gate de produto e operações

Com `COMMUNITY_PRODUCT_ACCESS_ENABLED=true`, ingestão de eventos e agregados
próprios exigem `PRODUCT_USE:TELEMETRY`. A ingestão reavalia antes do lote,
deriva a identidade do JWT com a chave histórica e rejeita campos de identidade
enviados pelo cliente. A decisão negativa não escreve eventos, outbox ou rejeições.

Agregados globais, dashboard, contagem e diagnóstico são operações do cliente
de plataforma `bookrush-behavior-operations-s2s`, com audience `bookrush-behavior`
e scope `bookrush.behavior.operations.read`. A reconstrução usa
`bookrush.behavior.rebuild`. Os endpoints de recomendação e métricas de publisher
mantêm seus escopos e audience específicos. O scraper Prometheus e os health
checks mantêm suas políticas atuais.

Provisione `bookrush-behavior-product-access-s2s` com
`bookrush-behavior-product-access-s2s=PRODUCT_USE:TELEMETRY` na allowlist do
avaliador. `KEYCLOAK_COMMUNITY_CLIENTS_ENABLED=true` inclui as credenciais
de community no reconciliador. Use as variáveis de `.env.example`, configure
HTTPS e valide os workloads antes de habilitar o gate.

O E2E local dos três serviços usa
`BOOKRUSH_E2E_COMMUNITY=1 bash scripts/test-premium-family-e2e.sh`.
