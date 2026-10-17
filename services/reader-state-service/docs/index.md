# reader-state-service

Reader library, progress and streak state. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 persist library, progress, bookmarks and reading-day projections per subject.

V4 adiciona recentes e sessões; updates são restringidos ao subject autenticado.

V5 adiciona locators imutáveis para progresso/bookmarks e a projeção persistente
da planta. `GET /api/v1/reader/plant` materializa a projeção por subject usando
a fórmula versionada `reading-minutes-v1`: cada 60 minutos acumulados avança um
estágio, limitado ao estágio 5; o streak atual e a data medida são retornados
junto com o total de minutos. Encerrar uma sessão atualiza a projeção na mesma
transação da leitura diária, tornando retries e concorrência de sessões seguros.

Every mutation requires the authenticated `(iss, sub)` principal. Opening a
book is an idempotent upsert in `reader_state.recent`, so retries from a mobile
client do not create duplicate rows. The controller test covers the SQL
upsert and the unauthenticated rejection path.

The upsert keeps the service's canonical SQL contract in lowercase compact
form (`insert into ... on conflict ...`). This representation is covered by
the unit test so refactors cannot silently change the statement used by the
reader-state adapter.

Eventos `BOOK_OPEN`, `READ_PROGRESS` e `READ_SESSION` são enviados ao
behavior-service com o bearer do cliente, chave idempotente e até três
tentativas; o behavior-service persiste o evento e seu outbox na mesma
transação.
