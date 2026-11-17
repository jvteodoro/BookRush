# API administrativa

## Reporting versionado

As rotas de reporting usam o namespace público `/api/admin/v1/reporting/**`.
O Traefik e o Nginx do `admin-web` encaminham esse namespace para o mesmo
`admin-service` que atende `/api/v1/admin/**`; isso mantém a facade separada
das operações administrativas legadas sem criar um segundo serviço.

`GET /api/admin/v1/reporting/reader-day?days=30` retorna o contrato
`dashboard-response-v1` a partir de snapshots read-only do schema `reporting`.
O intervalo é limitado a 366 dias e exige `platform-admins`, `operators` ou
`analysts` no backend. `GET /api/admin/v1/reporting/health` expõe apenas
freshness, watermark, versão e estado da projeção. Quando a origem ainda não
possui população classificada, a resposta permanece `UNTRUSTED` com
`population=UNCLASSIFIED`; ausência de linhas é `NO_DATA`, não zero.
O snapshot `reader_day` inclui DAU, WAU, MAU, novos e recorrentes derivados de
eventos de leitura significativos. Usuários distintos são contados pelo par
`identity_issuer` + `identity_subject`; eventos sem qualquer parte da identidade
são excluídos dos totais de usuários. A dimensão de plataforma é mantida, então
somar plataformas pode contar a mesma identidade mais de uma vez. A população
continua explicitamente `UNTRUSTED` até a proveniência ser classificada.

`GET /api/admin/v1/reporting/reading-daily` inclui buckets diários de duração
de sessão: `<1m`, `1–5m`, `5–15m` e `15m+`. Os limites são semiabertos
([0,60), [60,300), [300,900), [900,86400]); duração negativa é tratada como
zero e valores acima de 24 horas são limitados a 24 horas antes da agregação.
`GET /api/admin/v1/reporting/library-state` retorna somente um snapshot atual
agregado de pares salvos, obras e subject keys distintos. `reader_state.library`
remove a linha ao desfazer um save e não guarda eventos de transição; por isso a
API não apresenta esse estado como série histórica. O watermark da fonte é nulo
porque a tabela não conserva timestamp de atualização/exclusão.
No `/executive-summary`, `daily_peak_users` é o pico diário calculado por
issuer/subject distinto diretamente dos eventos elegíveis, deduplicado entre
plataformas. As séries por plataforma permanecem separadas e não devem ser
somadas para produzir o total geral.

`GET /api/admin/v1/reporting/feed-daily?days=30` expõe o ledger de entrega do
feed e outcomes com `feed-attribution-v4-mature-viewable-ctr-v1`. O CTR usa
impressões viewable com pelo menos sete dias como denominador e aberturas
atribuídas distintas por impressão nessa coorte como numerador. A resposta
inclui `eligible_viewable_count` e `mature_attributed_open_count`. A atribuição só liga a abertura à
impressão exata; fallback por item exige que o evento de exposição identifique
o mesmo `impressionId`. A maturidade começa em `viewable_at`; impressões legadas
sem esse timestamp permanecem fora do CTR elegível. Além do funil
atribuído, a projeção agrega `DWELL`/`EXCERPT_DWELL` por `visitId`, exposição,
sujeito, livro e janela de 24 horas, retornando `dwell_observations`,
`dwell_seconds` e `skipped_visits`. Eventos repetidos com o mesmo `visitId`
contam uma visita; a maior duração válida por visita evita double count. Skips
são registrados por ação explícita de avançar no feed, nunca inferidos da
ausência de abertura. Durações não numéricas são excluídas e cada exposição é
agregada antes do join com outcomes, evitando multiplicação por múltiplas ações.
`GET /api/admin/v1/reporting/pipeline?days=30`
expõe snapshots dos itens e fases do analytics. Essas projeções também ficam
`UNTRUSTED` enquanto a proveniência de população não for exportada pelos
bounded contexts.

`GET /api/admin/v1/reporting/recommendation-daily?days=30` agrega requests,
contagens de candidatos/pontuados/selecionados, itens, impressões, viewability,
livros únicos e distribuição de rank por modelo, estratégia e caminho de
personalização e categoria de plataforma. `platform` só aceita `web`/`mobile`
do header coarse encaminhado pelo Reader BFF; ausente ou inválido resulta em
`UNKNOWN`, sem guardar User-Agent. Também retorna a taxa de impressões
viewable repetidas para o mesmo leitor/livro nos 30 dias anteriores e livros
distintos com viewability por dia. O ledger guarda a cardinalidade do conjunto
candidato, mas não seus livros individuais nem o universo elegível; essas
contagens não representam cobertura do catálogo. A comparação é
observacional e não declara causalidade.

`GET /api/admin/v1/reporting/recommendation-quality-daily?days=30` agrega
outcomes atribuídos a itens do ledger por estratégia e tipo de outcome,
incluindo sujeitos/livros únicos e rank mediano.

`GET /api/admin/v1/reporting/recommendation-profile-quality-daily?days=30`
agrega requests por buckets de confiança e tamanho do perfil, cobertura de
fadiga no conjunto candidato e estado cold-start. A projeção não expõe valores
de perfil nem identificadores de sujeito; buckets antigos aparecem como
`UNKNOWN`.
Confiança usa faixas de 25 pontos percentuais; tamanho é a quantidade de sinais
(`EMPTY`, `SMALL` até 3, `MEDIUM` até 10, `LARGE` acima de 10); fadiga é a
proporção de candidatos com penalidade ativa (`NONE`, `LOW` abaixo de 25%,
`MEDIUM` abaixo de 75%, `HIGH` a partir de 75%).

`GET /api/admin/v1/reporting/book-engagement?days=30&minReaders=5&limit=50`
agrega obras e idiomas a partir de eventos e `behavior.daily_book`, ocultando
linhas abaixo do limiar mínimo de leitores para evitar exposição de amostras
pequenas. `page=0..10000` pagina a consulta com `LIMIT limit+1/OFFSET` e o
response informa `page`, `pageSize` e `hasNext`.

`GET /api/admin/v1/reporting/book-engagement-trends?days=30&minReaders=5&top=5`
seleciona separadamente as cinco obras e os cinco idiomas com mais minutos
ativos na janela, e devolve a série diária apenas nos dias em que cada
agregado atende `minReaders`. A ordenação usa os minutos dos dias elegíveis;
períodos abaixo do limiar não entram no ranking nem na série. `top` aceita de 1
a 10, `days` de 1 a 366 e a rota exige papel analyst/operator/admin.

`GET /api/admin/v1/reporting/executive-summary?days=30` retorna KPIs agregados
do período atual e do período anterior de mesma duração, além dos
denominadores do funil viewable→open→read→complete. A comparação é
observacional e permanece `UNTRUSTED` enquanto a população não estiver
classificada.

`GET /api/admin/v1/reporting/quality/attribution?days=30` expõe a distribuição
de outcomes por `ATTRIBUTED`, `ORGANIC`, `OUTSIDE_WINDOW` e
`INVALID_REFERENCE`. `GET /api/admin/v1/reporting/quality/reconciliation?days=30`
compara o ledger de impressões com eventos de viewability, destacando
duplicidades e IDs sem correspondência. Esses endpoints são read-only, limitados
a 366 dias e protegidos pelo mesmo RBAC de reporting.

`GET /api/admin/v1/reporting/quality/events?days=30` agrega eventos aceitos,
rejeitados, duplicidades classificadas e o atraso do outbox. Payloads e textos de
erro não fazem parte da projeção.

`GET /api/admin/v1/reporting/quality/event-schema?days=30` agrega por tipo de
registro, versão de schema, versão do cliente e motivo de rejeição, sem expor
payloads.

`GET /api/admin/v1/reporting/recommendation-position-daily?days=30` agrega
outcomes atribuídos por bucket de posição (`1-3`, `4-10`, `11-24`, `25+`), sem
identificadores de usuário.

`GET /api/admin/v1/reporting/excerpt-performance?days=30&minImpressions=20&limit=200`
agrega impressões, viewability, CTR observacional, leituras e conclusões por
excerpt/livro/modelo. A rota é protegida pelo RBAC de reporting, não retorna o
texto do trecho e suprime linhas abaixo do limiar de impressões. `page=0..10000`
usa a mesma paginação limitada. O valor é descritivo e não representa
causalidade. O CTR usa `mature_attributed_open_count` sobre
`eligible_viewable_count`; a janela de sete dias inicia em `viewable_at`.

Todos os responses de série normalizam `sourceWatermark`/`refreshedAt` para
ISO-8601 e informam `freshnessSeconds`, `warnings` e `status=STALE` quando a
idade ultrapassa `BOOKRUSH_REPORTING_STALE_AFTER_SECONDS` (900 s por padrão).
Leituras GET recebem cache privado de 30 s por padrão, sempre com
`Vary: Authorization`; respostas 4xx/5xx recebem `Cache-Control: no-store`.
No gateway, o namespace possui rate limit inicial de 30 requisições/s por
cliente, burst 60. Esses valores são operacionais e calibráveis, não SLOs.

`GET /api/admin/v1/reporting/experiment-daily?days=30` expõe assignments
persistidos por variante e guardrails agregados de requests, viewability,
abertura, leitura e conclusão. O CTR usa apenas impressões viewable maduras de
sete dias, com aberturas deduplicadas por impressão. A rota não transforma
comparação observacional em efeito causal; ela só é populada quando um
experimento explicitamente habilitado no recommendation-service registra sua
variante no ledger.

`GET /api/admin/v1/reporting/recommendation-position-funnel-daily?days=30`
expõe impressões, viewables elegíveis, aberturas atribuídas e CTR por bucket de
rank, estratégia, modelo e plataforma. O CTR usa somente coortes maduras de
sete dias; outcomes vinculam-se à impressão viewable exata.

`GET /api/admin/v1/reporting/quality/access-audit?days=30` agrega ações
administrativas por dia, quantidade de eventos, atores distintos e targets
distintos. A resposta nunca contém `actor_subject`, target ou detalhes de
auditoria; o endpoint é protegido pelo RBAC de reporting.

`GET /api/admin/v1/reporting/social-daily?days=30` e
`GET /api/admin/v1/reporting/publisher-daily?days=30` expõem agregados
read-only de interações/moderação e métricas de publicação. A resposta mantém
proveniência e permanece `UNTRUSTED` enquanto a população não for classificada.
Em `social-daily`, os campos `behavior_*_events` contam os eventos de
interação aceitos por dia (likes/unlikes, saves/unsaves, shares e comentários);
atores e obras distintos também são agregados. Eles permanecem separados dos
contadores de `social.activity`, pois descrevem fontes e semânticas diferentes.

`GET /api/admin/v1/reporting/moderation-daily?days=30` expõe estados, percentis
de resolução e contagens de casos `OPEN`/`IN_REVIEW` nas faixas de idade <1,
1–3, 3–7 e 7+ dias, sem texto de denúncia. As faixas refletem a idade no
momento da atualização, agrupada pela data de criação; não são um SLA aprovado.
`GET
/api/admin/v1/reporting/publisher-workflow-daily?days=30` expõe o funil de
eventos diários de submissão, revisão, publicação e atestados. Para o funil
cohort-based, `GET
/api/admin/v1/reporting/editorial-submission-funnel-daily?days=30` agrupa por
data da submissão e conta submissões distintas que já alcançaram revisão,
aprovação, publicação e publicação ativa. Coortes recentes podem continuar em
andamento; não há taxa de conversão ou SLA inferidos. `GET
/api/admin/v1/reporting/rights-daily?days=30` agrega decisões de direitos por
ação/status, sem evidências ou sujeitos. `GET
/api/admin/v1/reporting/rights-status-daily?days=30` agrega decisões do catálogo
e atestações de publisher em linhas separadas por fonte, tipo de direito,
território e estado. Decisões aprovadas são rotuladas como ativas, expiradas ou
ainda não vigentes segundo seu intervalo temporal; revogação e atestação são
contadas separadamente. É um resumo operacional dos registros, sem inferir uma
conclusão jurídica sobre os direitos.

`GET /api/admin/v1/reporting/export/{dataset}?days=30` baixa CSV de uma
allowlist de agregados de reporting. Requer papel de operador/plataforma,
limita a resposta a 10.000 linhas e registra `REPORTING_DATA_EXPORTED` no
`admin.audit_log` com dataset, período e quantidade, sem registrar conteúdo
exportado. Export de engajamento por obra aplica mínimo de cinco leitores e
proteção contra fórmulas em células CSV.

`GET /api/admin/v1/reporting/content-supply?days=30` expõe snapshots da oferta
real de conteúdo: livros ativos, reader-ready (asset disponível e distribuição
permitida por licença ou decisão vigente), excerpt-ready (excerpt elegível num
asset reader-ready), versões de assets disponíveis, runs de analytics
concluídos e excerpts elegíveis/rankeados. Snapshots históricos anteriores à
definição reader-ready retornam `null` nessas duas métricas para não inferir
zeros retroativos. `GET /api/admin/v1/reporting/content-coverage?days=30`
agrupa livros ativos por idioma e
bucket `RIGHTS_ALLOWED`/`RIGHTS_NOT_CONFIRMED` e informa cobertura de assets
ativos/disponíveis, reader-ready, análises concluídas, excerpts elegíveis e
excerpts rankeados. Livros e excerpts são contados sem expor identificadores;
taxas devem usar o denominador da etapa anterior e ficam ausentes quando zero.
`GET /api/admin/v1/reporting/content-rank-scores?days=30` expõe decis do score
de excerpts agrupados pela combinação exata de ranker, versão e hash de
configuração, além do idioma e bucket de direitos. Cada linha informa faixa
observada e contagem; escalas de configurações distintas não são combinadas.
`GET /api/admin/v1/reporting/excerpt-production?days=30` agrega excerpts por
idioma, bucket de direitos, método de geração, resultado (`RANKED`,
`ELIGIBLE_UNRANKED`, `RANKING_EXCLUDED`, `STRUCTURALLY_EXCLUDED`) e motivo
limitado a códigos conhecidos ou `OTHER`. A fonte não registra estados de
aprovação/publicação editorial, que não são inferidos por este endpoint.
`GET /api/admin/v1/reporting/import-quality-daily?days=30` agrega tentativas
por origem, estado, bucket de erro, estado atual de direitos e estado do asset
vinculado. `retry_attempt_count` conta tentativas com `attempt_number > 1`;
repetições não são rotuladas como duplicidades. A coorte diária usa conclusão
da tentativa (ou criação quando ainda aberta); direitos refletem a decisão
vigente no instante da atualização do snapshot.
`GET /api/admin/v1/reporting/backlog?days=7` agrega os itens pendentes da fila
de ingestão e dos jobs de analytics, idade do item mais antigo, falhas e itens
RUNNING acima de 15 minutos; também expõe frescor/watermark das projeções. A
idade de worker é um indicador operacional, não um SLA de publicação aprovado.
`GET /api/admin/v1/reporting/ingestion-daily?days=30` expõe jobs e itens de ingestão
por estado, preservando falhas e watermark. Ambos são read-only, versionados,
protegidos pelo RBAC de reporting e retornam `NO_DATA` quando a origem não tem
registros; não substituem ausência por zeros.

`GET /api/admin/v1/reporting/ingestion-queue?days=30` expõe snapshots temporais
da fila durável `catalog.ingestion_task`: `PENDING`, `RETRY_WAIT`, `RUNNING`,
`FAILED`, `DONE` e `CANCELLED`. Para estados pendentes/de retry, a idade é medida
desde a criação do task; leases expirados são contados em linhas `RUNNING`.
Os snapshots não incluem IDs de job/task nem mensagens de erro.

`GET /api/admin/v1/reporting/pipeline-phases?days=30` expõe duração p50/p95/p99
por fase, tentativas, falhas por código e leases presos há mais de 15 minutos.
Os percentis são calculados sobre durações observadas e permanecem ausentes
quando não há duração válida.

`GET /api/admin/v1/reporting/editorial-quality-daily?days=30` expõe agregados
diários das campanhas de anotação: itens amostrados e por estado, anotações
submetidas, annotators distintos, discordâncias, adjudicações e p50/p95 do
tempo observado. O contrato não retorna texto, subject ou identificadores de
excerpt; a população permanece `UNCLASSIFIED` até a governança de amostra ser
formalmente classificada.

`GET /api/admin/v1/reporting/reading-daily?days=30` expõe sessões, segundos
ativos, percentis de duração, livros abertos e saves; `reader-day` mantém a
dimensão coarse `platform` (`web`, `mobile` ou `UNKNOWN`) sem user-agent bruto.
`GET
/api/admin/v1/reporting/retention-cohort?days=366` expõe D1/D7/D30 por coorte;
coortes ainda imaturas retornam `mature=false` e não recebem uma taxa.
`GET /api/admin/v1/reporting/reading-progress-daily?days=30` agrupa pares
leitor/obra pelo primeiro READ_SESSION associado à obra, com fallback ao
primeiro READ_PROGRESS. Cada coorte conta pares que já alcançaram pela primeira
vez 10%, 50% e 100% até o último refresh; progresso repetido não duplica o par.
O endpoint também agrega eventos BOOK_SAVE/BOOK_UNSAVE e leitores distintos
por ação. Não expõe subjects/obras, não reconstrói o estado atual da biblioteca
e não infere taxa ou SLA para coortes recentes.
`GET /api/admin/v1/reporting/reading-habits-daily?days=30` agrega leitores
ativos, minutos e livros concluídos por dia, além de faixas exclusivas para o
tamanho do streak consecutivo que termina naquele dia (1, 2–6, 7–29 ou 30+).
A projeção não guarda subjects; como `reader_state.reading_day` não oferece
timestamp de atualização, o watermark fica ausente e o estado de população
permanece `UNCLASSIFIED`.

`POST /api/v1/admin/audit` registra ação de administração sem armazenar tokens; `GET /api/v1/admin/audit` consulta os últimos eventos. O subject vem da identidade autenticada.

## Usuários

`GET /api/v1/admin/users?search=&page=0&size=25` pesquisa e pagina todas as
contas do realm de produto, inclusive aquelas sem perfil local. Somente tokens
do realm de plataforma com os papéis canônicos `platform-admin` ou
`platform-operator` — ou com os grupos legados `platform-admins` e `operators`
— podem chamar a rota.

`POST /api/v1/admin/users/{subjectKey}/disable` e `/enable` recebem
`{"reason":"..."}`. A alteração é confirmada no Keycloak antes da auditoria;
desabilitar também revoga as sessões do leitor. Motivos vazios, paginação fora
dos limites e tentativa de alterar a própria identidade são rejeitados.

## Moderação

`GET /api/v1/admin/moderation/comments` lista comentários pendentes ou já
decididos. `POST /api/v1/admin/moderation/comments` recebe `commentId` e
`visibility` (`VISIBLE`, `HIDDEN` ou `REMOVED`) e registra a decisão somente
para usuários com papel de operador.

## Moderação completa de comentários

O admin-service é a fachada administrativa de moderação existente. O proxy do
admin-web encaminha `/api/v1/admin/**` ao serviço; o gateway público mantém esse
mesmo contrato. Exige token novo do realm `bookrush-platform`, audience
`bookrush-ingestion` e role `platform-admin`/`platform-operator` (ou grupos legados
já aceitos). Tokens expirados são rejeitados. CORS mantém a lista explícita de
origens em `BOOKRUSH_CORS_ALLOWED_ORIGINS`.

- `GET /api/v1/admin/moderation/comments/page`: `search` até 200 caracteres,
  `visibility` vazio ou `VISIBLE`/`HIDDEN`/`REMOVED`, `page >= 0`, `size` entre
  1 e 100. Retorna `items`, `page`, `size`, `totalItems`, `last`. Busca literal
  em texto, autor, subject e IDs do livro/comentário; ordenação por criação e ID decrescentes.
- `POST /api/v1/admin/moderation/comments`: UUID `commentId`, `visibility` e
  `reasonCode` obrigatório com 3 a 80 caracteres após trim. Ocultar/remover
  retira o comentário da listagem social; restaurar volta a disponibilizá-lo.
  O conteúdo original é preservado. O estado anterior e novo, motivo, operador
  e data são auditados atomicamente; repetir o estado atual retorna `updated:
  false` sem duplicar histórico. Decisões concorrentes bloqueiam a mesma linha
  do comentário antes de ler o estado anterior.
- `GET /api/v1/admin/moderation/comments/{commentId}/history`: últimas 100
  decisões, mais recentes primeiro, com `previous_visibility`, `visibility`,
  `reason`, `actor_subject`, `created_at`.

Erros: 400 para filtros, paginação ou decisão inválidos; 401 para token ausente,
inválido ou expirado; 403 sem permissão de operador; 404 para comentário ausente.
A listagem antiga sem paginação permanece disponível para consumidores existentes.
Denúncias continuam com ciclo independente: mudar visibilidade não resolve nem
ignora denúncias automaticamente.

A implementação usa tabelas já existentes, sem modificar migrations aplicadas.
Validar com os testes Java e `scripts/test-admin-user-management-e2e.sh`, que
exercita PostgreSQL 16 e Keycloak descartáveis pelo proxy do admin-web, incluindo
busca, paginação, justificativa, histórico e efeito na API social de leitura.

O teste de integração também exige 403 para um token recém-emitido sem papel de
operador, 401 para token inválido/ausente e preflight da origem autorizada. Suas
imagens com tags aleatórias são removidas ao final, preservando o cache de build
compartilhado e todos os volumes persistentes do projeto.

Comentários com `author_deleted_at` são exibidos como `REMOVED`, com o instante
da remoção pelo autor. Uma tentativa de restaurar recebe 409, mantendo denúncias
e decisões anteriores. A marca é propriedade do social-service (migration V8),
que deve ser implantado antes da nova revisão administrativa em deploy parcial.
# Histórico Jenkins e releases

`POST /api/admin/v1/reporting/internal/release-events` aceita eventos
`BUILD`, `DEPLOY` e `ROLLBACK` apenas com token S2S do realm
`bookrush-platform`, audience `bookrush-admin-reporting` e scope
`bookrush.reporting.release.write`. A rota é read-only para a UI: o endpoint de
ingestão valida event id, evento/serviço e limites dos campos; retries iguais
são idempotentes: reenvios preservam o `occurredAt` da primeira gravação.
Reutilizar o event id com outros metadados retorna 409.

`GET /api/admin/v1/reporting/release-events?days=30&limit=100` exige papel de
analista ou operador e devolve até 500 eventos. Nunca armazena bearer, credencial
ou console log. O digest é opcional para eventos de build sem imagem publicada.
