# reader-state-service

Reader library, progress and streak state. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 persist library, progress, bookmarks and reading-day projections per subject.

V4 adiciona recentes e sessões; updates são restringidos ao subject autenticado.

V5 adiciona locators e a projeção legada. V6 introduz a planta diária
`daily-reading-v2`, independente do histórico anterior. Intervalos autenticados
em `POST /api/v1/reader/plant` são idempotentes por eventId e
somados por dia no fuso America/Sao_Paulo. Ao atingir 900 segundos, a planta
cresce ou se recupera; cada dia encerrado sem meta alterna tristeza e regressão
até o estágio 0. O estado é recalculado por usuário e exposto em
`GET /api/v1/reader/plant`. A contabilização depende do leitor visível e das
requisições recebidas pelo servidor.
O preflight da rota aceita apenas as origens explícitas de
`BOOKRUSH_CORS_ALLOWED_ORIGINS`; autenticação continua obrigatória para GET e POST.

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
