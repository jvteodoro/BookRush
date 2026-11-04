# API administrativa

## Reporting versionado

`GET /api/admin/v1/reporting/reader-day?days=30` retorna o contrato
`dashboard-response-v1` a partir de snapshots read-only do schema `reporting`.
O intervalo é limitado a 366 dias e exige `platform-admins`, `operators` ou
`analysts` no backend. `GET /api/admin/v1/reporting/health` expõe apenas
freshness, watermark, versão e estado da projeção. Quando a origem ainda não
possui população classificada, a resposta permanece `UNTRUSTED` com
`population=UNCLASSIFIED`; ausência de linhas é `NO_DATA`, não zero.
O snapshot `reader_day` inclui DAU, WAU, MAU, novos e recorrentes derivados de
eventos de leitura significativos; a população continua explicitamente
`UNTRUSTED` até a proveniência ser classificada.

`GET /api/admin/v1/reporting/feed-daily?days=30` expõe o ledger de entrega do
feed e outcomes com `attribution-v1`. `GET /api/admin/v1/reporting/pipeline?days=30`
expõe snapshots dos itens e fases do analytics. Essas projeções também ficam
`UNTRUSTED` enquanto a proveniência de população não for exportada pelos
bounded contexts. Dwell permanece não instrumentado e não é apresentado como
zero.

`GET /api/admin/v1/reporting/recommendation-daily?days=30` agrega requests,
itens, impressões, viewability, livros únicos e distribuição de rank por modelo,
estratégia e caminho de personalização. A comparação é observacional e não
declara causalidade.

`GET /api/admin/v1/reporting/recommendation-quality-daily?days=30` agrega
outcomes atribuídos a itens do ledger por estratégia e tipo de outcome,
incluindo sujeitos/livros únicos e rank mediano.

`GET /api/admin/v1/reporting/book-engagement?days=30&minReaders=5&limit=50`
agrega obras e idiomas a partir de eventos e `behavior.daily_book`, ocultando
linhas abaixo do limiar mínimo de leitores para evitar exposição de amostras
pequenas.

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

`GET /api/admin/v1/reporting/social-daily?days=30` e
`GET /api/admin/v1/reporting/publisher-daily?days=30` expõem agregados
read-only de interações/moderação e métricas de publicação. A resposta mantém
proveniência e permanece `UNTRUSTED` enquanto a população não for classificada.

`GET /api/admin/v1/reporting/moderation-daily?days=30` expõe estados e percentis
de resolução sem texto de denúncia. `GET
/api/admin/v1/reporting/publisher-workflow-daily?days=30` expõe o funil de
submissões, revisão, publicação e atestados. `GET
/api/admin/v1/reporting/rights-daily?days=30` agrega decisões de direitos por
ação/status, sem evidências ou sujeitos.

`GET /api/admin/v1/reporting/content-supply?days=30` expõe snapshots da oferta
real de conteúdo: livros ativos, versões de assets disponíveis, assets com run
de analytics concluído e excerpts elegíveis/rankeados. `GET
/api/admin/v1/reporting/ingestion-daily?days=30` expõe jobs e itens de ingestão
por estado, preservando falhas e watermark. Ambos são read-only, versionados,
protegidos pelo RBAC de reporting e retornam `NO_DATA` quando a origem não tem
registros; não substituem ausência por zeros.

`GET /api/admin/v1/reporting/pipeline-phases?days=30` expõe duração p50/p95/p99
por fase, tentativas, falhas por código e leases presos há mais de 15 minutos.
Os percentis são calculados sobre durações observadas e permanecem ausentes
quando não há duração válida.

`GET /api/admin/v1/reporting/reading-daily?days=30` expõe sessões, segundos
ativos, percentis de duração, livros abertos e saves. `GET
/api/admin/v1/reporting/retention-cohort?days=366` expõe D1/D7/D30 por coorte;
coortes ainda imaturas retornam `mature=false` e não recebem uma taxa.

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
