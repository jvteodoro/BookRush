# behavior-service

Accepted events are written transactionally with a durable outbox. The
maintenance job rebuilds derived aggregates and retention only. Set
`BEHAVIOR_OUTBOX_SINK_URL` to an internal authenticated event sink to enable
delivery; failed deliveries remain pending and increment `attempts` with an
error, rather than being marked as published.

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

The ingestion endpoint derives `identity_subject` from the authenticated
principal and ignores identity fields supplied in the event body. Batches are
limited to 100 events and payloads to 16 KiB; retries remain idempotent through
the unique `event_key` constraint. This prevents a browser from attributing an
event to another user.

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

## Outbox downstream

Eventos aceitos entram na outbox e são enviados pelo dispatcher para
`BEHAVIOR_OUTBOX_SINK_URL`. O ambiente Compose usa o consumidor interno do
`book-analytics-service` em `/api/internal/v1/behavior/events`; a autenticação
entre os serviços usa `BEHAVIOR_OUTBOX_SINK_TOKEN`, mantido somente no `.env`
protegido. A linha só recebe `published_at` depois de uma resposta 2xx.
