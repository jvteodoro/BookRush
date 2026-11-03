# reader-state-service

Reader library, progress and streak state. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Migrations V2/V3 persist library, progress, bookmarks and reading-day projections per subject.

V4 adiciona recentes e sessões; updates são restringidos ao subject autenticado.

V6 adiciona `book_id` nullable à sessão de leitura para manter compatibilidade
com sessões antigas. Novas sessões podem receber `bookId`; ao encerrá-las,
`READ_SESSION` é publicado para esse livro e preserva `recommendationItemId` e
`impressionId` quando os headers de correlação vierem do feed.
V7 persiste essa origem na própria sessão ao iniciar; o encerramento reutiliza
os IDs persistidos quando o cliente não repete os headers, sem associar a sessão
à última recomendação visualizada.

V5 adiciona locators imutáveis para progresso/bookmarks e a projeção persistente
da planta. `GET /api/v1/reader/plant` materializa a projeção por subject usando
a fórmula versionada `reading-minutes-v1`: cada 60 minutos acumulados avança um
estágio, limitado ao estágio 5; o streak atual e a data medida são retornados
junto com o total de minutos. Encerrar uma sessão atualiza a projeção na mesma
transação da leitura diária, tornando retries e concorrência de sessões seguros.
Além da projeção legada, V6 introduz a planta diária
`daily-reading-v2`, independente do histórico anterior. Intervalos autenticados
em `POST /api/v1/reader/plant` são idempotentes por eventId e
somados por dia no fuso America/Sao_Paulo. Ao atingir 900 segundos, a planta
cresce ou se recupera; cada dia encerrado sem meta alterna tristeza e regressão
até o estágio 0. O estado é recalculado por usuário e exposto em
`GET /api/v1/reader/plant`. A contabilização depende do leitor visível e das
requisições recebidas pelo servidor.
O preflight da rota aceita apenas as origens explícitas de
`BOOKRUSH_CORS_ALLOWED_ORIGINS`; autenticação continua obrigatória para GET e POST.

Na sequência Flyway efetiva, a planta diária ocupa V6; as colunas de sessão
ocupam V7 (`book_id` e atribuição) e V8 (correlação adicional). A numeração
evita duas migrations V6 quando os contratos de planta e de atribuição são
implantados juntos.

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

Ao atingir 100% de progresso, o serviço publica `BOOK_COMPLETE` uma única vez
por subject/livro usando uma chave idempotente estável, preservando a correlação
do feed quando aplicável. A origem direta/search continua sem correlação
inventada.

## Livros vistos

Um livro é **visto** quando o conteúdo carrega com sucesso no leitor. Passar pelo
feed, abrir somente as informações, salvar na estante ou tentar carregar uma
publicação que falha não conta. Visto não significa leitura concluída.

O cliente chama `POST /api/v1/reader/books/{bookId}/opened` somente após
`Readium.load` bem-sucedido. A transação persiste histórico e `seen_book`;
reaberturas atualizam last_seen_at, preservando first_seen_at. Eventos de
recomendação/correlação existentes são mantidos.

`GET /api/v1/reader/books/seen?bookIds=<uuid>,<uuid>` retorna `{items:[{bookId,
firstSeenAt,lastSeenAt}]}` apenas para os IDs vistos pelo subject autenticado.
A lista exige 1–100 UUIDs (400 para inválida) e não depende do limite de 50
recentes. Sem token válido/issuer/audience corretos: 401. Não aceita identidade
fornecida pelo cliente. O acesso público atravessa o reader-bff, cujo CORS
permite somente origens explícitas, GET/POST e Authorization/Content-Type;
origem indevida retorna 403 e dependência indisponível retorna 502 no BFF.
Tokens do realm produto bookrush, audience account, sem scope extra.

Validação: `bash scripts/test-book-metadata-e2e.sh` usa serviços/imagens reais,
Keycloak e PostgreSQL 16 descartáveis com gateway Traefik versionado. Não usa
`.env` nem dados persistentes existentes. Inclui upgrade V8→V9 e segundo boot
Flyway, isolamento por perfil e marca preservada além dos 50 recentes.
