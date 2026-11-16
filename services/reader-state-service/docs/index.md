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
# Reader sessions and attribution

`POST /api/v1/reader/sessions` starts a reading session with an optional
`bookId`. Feed-origin sessions may include recommendation and impression
headers; direct library/search sessions omit them. `POST /sessions/{id}/end`
publishes `READ_SESSION` with the persisted session origin and active seconds.

`PUT /books/{bookId}/progress` publishes `READ_PROGRESS` and, at 100%, an
idempotent `BOOK_COMPLETE`; the frontend sends the correlation headers only
while the active reader session originated from a feed item.

Plant intervals remain a separate gamification/streak aggregate. Reader
sessions feed recommendation behavior/outcomes; the two paths do not share
event requests or double-count active time.

The web Readium bridge captures feed attribution before mounting the
publication, starts the session only after the publication is validated, and
ends the same session on reader unmount or page hide. Leaving the reader
clears the origin, so a later library/search opening of the same book remains
direct and un-attributed.

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

## Marca??es de leitura (Readium)

`GET` e `POST /api/v1/reader/books/{bookId}/bookmarks` pertencem ao
reader-state-service e s?o expostos pelo reader-bff-service. O principal do
bearer do realm `bookrush` (audience `account`, sem scope adicional) define o
dono; o corpo n?o pode selecionar outro leitor. O gateway Traefik encaminha
`PathPrefix(/api/v1/reader/books)` ao BFF. CORS usa as origens expl?citas de
`BOOKRUSH_CORS_ALLOWED_ORIGINS` e os m?todos existentes GET/POST/OPTIONS com
Authorization/Content-Type; n?o foi necess?rio abrir novas origens.

O POST aceita `{ "locator": { "href": "...", "type": "text/html",
"locations": { "progression": 0.4321 } } }`, com os demais campos Readium
preservados. N?o arredonde progression e n?o envie percentual como codepoint.
A migration V10 separa a unicidade de posi??es antigas da identidade JSONB do
locator. Repetir o mesmo locator retorna o ID persistido; posi??es pr?ximas
com o mesmo percentual arredondado permanecem distintas. O href inclui a
vers?o do conte?do. A retomada s? aceita recursos presentes na publica??o
aberta, para evitar navegar para uma vers?o diferente silenciosamente.

O GET retorna marca??es da conta/livro em ordem de cria??o decrescente; locator
? JSON serializado em string (ausente/null nas marca??es antigas). O frontend
converte a string antes de chamar o Readium. POSTs legados com
positionCodepoint continuam aceitos, mas o percentual incorreto de marca??es
antigas n?o permite reconstruir sua posi??o: elas aparecem indispon?veis.
Respostas: 200 sucesso, 400 corpo/UUID inv?lido, 401 autentica??o, 403
origem/autoriza??o, 502 depend?ncia indispon?vel atrav?s do BFF.

Valida??o de banco: execute `BookmarkDatabaseTest` com
`BOOKMARK_TEST_JDBC_URL` apontando exclusivamente para PostgreSQL descart?vel
vazio, usu?rio `postgres`, senha de fixture `bookrush-test`. O teste aplica at?
V9, grava marca??o antiga, aplica V10, revalida e cobre ID est?vel, posi??es
pr?ximas, isolamento por leitor, UUID, JSON e timestamptz.

Para validar a revis?o implantada, publique a branch e use o fluxo Jenkins
autorizado; verifique a imagem efetiva de reader-state e frontend. No gateway
p?blico, use sess?o/token novo e a origem real para testar 401 sem bearer,
401 com bearer inv?lido, 200 com bearer v?lido, preflight e origem rejeitada.
Na interface, marque duas posi??es pr?ximas, reabra o livro/sess?o, liste e
retome ambas; simule API indispon?vel e confirme que n?o h? toast de sucesso.
N?o se considera validado em produ??o somente pelo build ou teste unit?rio.

## Progresso para retomada autom?tica

GET/PUT `/api/v1/reader/books/{bookId}/progress` retornam e gravam o locator
Readium por principal/livro. A coluna JSONB j? existe desde V5; nenhuma migration
nova ? necess?ria. PUT preserva os campos Readium e aceita percent entre 0 e 100,
inclusive movimentos para tr?s. Codepoint ? zero para o novo cliente e n?o deve
receber percentual. Clientes legados sem locator continuam aceitos e limpam o
locator antigo, evitando retomar uma refer?ncia desatualizada.

GET mant?m a resposta de zero ou uma linha e inclui locator como JSON string.
Os status, autoriza??o (realm bookrush, audience account, sem scope adicional),
rota BFF e origens CORS s?o os mesmos documentados para marca??es, com PUT no
lugar de POST. READ_PROGRESS e BOOK_COMPLETE mant?m seus eventos e correla??o.
A valida??o local cobre persist?ncia JDBC via mocks e a retomada no frontend;
a valida??o PostgreSQL usa BookmarkDatabaseTest em banco descart?vel. O smoke
p?blico deve testar sair/reabrir, duas contas, nova sess?o, progresso zero, erros
HTTP e posi??o de uma vers?o anterior com token novo/origem real. Deploy e
imagem efetiva precisam ser verificados antes de afirmar valida??o em produ??o.

## Nomes e exclus?o de marca??es

V11 adiciona `bookmarks.name` (VARCHAR(120), NOT NULL, default vazio). POST
`/api/v1/reader/books/{bookId}/bookmarks` aceita name opcional para manter clientes
antigos compat?veis; se fornecido deve ter entre 1 e 120 caracteres ap?s trim.
Repetir a mesma posi??o sem name mant?m o nome existente. A coluna note conserva
sua finalidade anterior, separada do nome.

PUT `/api/v1/reader/books/{bookId}/bookmarks/{bookmarkId}` exige `{ "name": "..." }`
e retorna 200 com a marca??o atualizada. S? altera name, preservando ID, locator
e created_at. DELETE no mesmo caminho retorna 204 vazio. Ambas opera??es filtram
por ID, livro e principal autenticado. Aus?ncia, conta diferente ou livro errado
retornam 404; nome/UUID inv?lido retorna 400 e aus?ncia de autentica??o retorna
401. O BFF preserva esses status e traduz falhas upstream para 502.

O contrato p?blico mant?m realm bookrush, audience account e nenhuma permiss?o
adicional. O gateway encaminha o prefixo de livros para reader-bff; CORS p?blico
j? permite PUT/DELETE, Authorization e Content-Type nas origens expl?citas
configuradas. N?o h? mudan?a de Keycloak ou abertura de novas origens.
A valida??o de banco est? em BookmarkDatabaseTest e exige PostgreSQL descart?vel;
consulte database/bookmark-names.md. Para smoke p?blico ap?s deploy, criar com
nome, listar, renomear, relistar, excluir, confirmar 204 e testar dois usu?rios,
livro errado, nome inv?lido e preflight com a origem real e token novo.
