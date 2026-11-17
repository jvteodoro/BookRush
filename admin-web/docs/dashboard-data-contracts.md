# Contratos de indicadores e fontes — dashboards da administração

> **Estado:** contrato e inventário revisados em 2026-11-17. Cada linha distingue implementação local de validação de runtime; o código local não prova migration aplicada, scrape ou população suficiente. Mudanças de métricas exigem versão e atualização conjunta dos painéis e documentação.

## Ownership, linhagem e classificação

| Domínio | Origem autoritativa existente | Relação com reporting | Instrumentação |
|---|---|---|---|
| Behavior | \`behavior.events\`, \`behavior.rejects\`, \`behavior.outbox\`, \`behavior.daily_book\`, \`behavior.user_book_signal\`, \`behavior.outcome_attribution\` (V12) | outcomes versionados guardam janela, status e evento de exposição correlacionado; **não** usar \`daily_book\` como prova de atribuição por impression | atribuição de 7 dias com exposição viewable; runtime/população a validar |
| Recommendation | \`recommendation.request\`, \`recommendation.item\`, \`recommendation.impression\` (\`viewable\`, \`viewable_at\`, \`rank\`, versões/razões) | reporting atribui outcomes à impressão viewable exata em janela de sete dias e calcula repetição por leitor/livro em 30 dias | PFV1/FeedMetrics parcialmente instrumentados; migrations/runtime pendentes |
| Reader-state | \`reader_state.session\` (book e correlation V7/V8), \`reader_state.progress\`, \`reading_day\`, \`library\` | fatos de leitura e atividade; sessão sem livro ≠ tempo por livro | SQL existente; telemetria contextual a auditar |
| Catalog / Ingestion | direitos, status, assets e versões; `catalog.ingestion_job/item/task`; arquivos no SeaweedFS | reporting gera snapshots de leitura da fila e disponibilidade; browser acessa apenas APIs autorizadas | `ingestion_queue_snapshot` e contadores `bookrush_ingestion_items_total{result}` instrumentados; scrape/runtime de produção pendente |
| Content Analytics | \`analytics.analysis_job\`, \`analysis_job_item\`, \`analysis_phase_timing\`, \`analysis_phase_log\`, excerpts e rank/versionamentos | snapshots batch e cobertura por versão; comparação de fases por tentativa | AnalyticsMetrics já presente |
| Publisher / Social / Admin | submission/review/publication, social interactions, denúncias/moderação | snapshots agregados no admin-service com autorização restrita; SO-05 liga estágios por coorte de submissão | publisher mantém lifecycle; reporting calcula coortes sem identidade ou título |
| Host e serviços | Actuator (`bookrush.storage`), `FeedMetrics`, cAdvisor, Keycloak HTTP metrics, Traefik Prometheus metrics, Jenkins | Prometheus/Loki/Tempo + exporters | Scrapes de gateway/Keycloak configurados em rede privada; outcomes HTTP não equivalem a eventos de login e scrape vivo segue pendente |
| Experimentos | assignment persistido por experimento/subject e variante | \`reporting.experiment_daily\` agrega requests, impressões e outcomes ligados à impressão viewable exata; CTR usa coorte madura de sete dias | assignment e projeção instrumentados localmente; runtime, amostra e validação estatística pendentes |

Snapshots cross-context devem ser gerados por jobs de leitura/autorização explícita, idealmente exports com manifesto \`schemaVersion\`, \`cutoff\`, \`highWatermark\`, \`rowCount\`, \`sha256\` e timestamps. O runtime de produção de um microsserviço não executará SELECT cross-schema de outro owner; a facade reporting lê **somente** projeções próprias. ACL e segregação de roles de exportação são obrigatórias.

\`bookrush_ingestion_items_total{result}\` tem enum de baixa cardinalidade:
\`discovered\`, \`acquired\`, \`normalized\`, \`processed\`, \`attempt_failed\`,
\`retry\` e \`failed\`. \`discovered\` conta itens aceitos em novo job;
\`acquired\` e \`normalized\` são eventos de etapa e podem ocorrer novamente
numa tentativa retry; \`attempt_failed\`/\`retry\` medem tentativas, enquanto
\`processed\`/\`failed\` medem resultados terminais. Esses contadores não
representam livros únicos.

### SO-03 — idade da fila de moderação

`reporting.moderation_daily` expõe contagens de reports ainda em `OPEN` ou
`IN_REVIEW` nas faixas <1 dia, 1–3 dias, 3–7 dias e 7+ dias. Os buckets são
calculados na atualização do snapshot e agrupados pela data de criação do
report; portanto, descrevem a idade atual dos casos não resolvidos em cada
coorte, não um histórico diário imutável. Não há SLA aprovado no domínio; os
limites são faixas de visualização e não significam violação de SLA.

O serving de recommendations correlaciona uma exposição `EXCERPT_IMPRESSION`
viewable à obra/identidade e ao item ou impression, escolhe a exposição válida
mais recente dentro de sete dias e persiste `exposure_event_id`, timestamp,
versão e estado em `behavior.outcome_attribution`. Somente `ATTRIBUTED` entra
nos numeradores de conversão; `ORGANIC`, `OUTSIDE_WINDOW` e
`INVALID_REFERENCE` permanecem separados na reconciliação. Uma referência
presente no payload por si só não comprova atribuição.

`reporting.recommendation_daily` agrega requests, candidatos recuperados,
candidatos com score de analytics, candidatos selecionados pelo ranking, itens
retornados, impressions, livros distintos e percentis de rank no grão
dia/model/strategy/feed-path/platform. As contagens de estágio são somadas por
request; rows históricas sem instrumentação permanecem nulas, sem serem
interpretadas como zero. `selected_count` descreve o resultado do ranking antes
do filtro final de elegibilidade; `item_count` descreve itens retornados.
`candidate_count` é o tamanho do conjunto de IDs válidos enviado ao ranking;
`scored_candidate_count` conta os IDs desse conjunto que receberam score do
Analytics; `selected_count` conta o resultado do ranking antes da filtragem
final. Esses totais não incluem o catálogo elegível completo.
`unique_books` e P50/P90 de rank são calculados sobre todos os itens do grupo,
não somando estatísticas por request. `viewable_unique_books` conta livros
distintos com viewability no dia; `repeat_viewable_count` conta impressões
viewable com outra impressão do mesmo leitor/livro nos 30 dias anteriores.
O ledger registra a cardinalidade do conjunto candidato por request, mas não
os livros candidatos individuais nem o total de livros elegíveis do catálogo;
essas métricas não devem ser tratadas como cobertura de catálogo. Comparações
por strategy são descritivas, não causais, sem assignment e holdout persistidos.
As projeções de qualidade e posição resolvem outcomes pelo impression ledger;
aceitam a chave de impression quando o outcome legado não possui item ID e
exigem consistência entre os dois identificadores quando ambos estão presentes.

## Modelo canônico proposto para fatos analíticos

\`\`\`text
reporting.feed_impression_fact(
  impression_id PK, recommendation_item_id, recommendation_request_id,
  book_id, excerpt_id NULL, subject_pseudonym,
  issued_at, served_at, first_viewable_at NULL, rank, model_version,
  strategy, feed_path, population, platform, source_version
)
reporting.attributed_outcome_fact(
  source_event_id PK, impression_id NULL, recommendation_item_id NULL,
  book_id, subject_pseudonym, outcome_type, occurred_at, attribution_window,
  attribution_rule_version, attribution_status
)
reporting.reader_day(
  day, population, platform, active_users, newly_active_users,
  sessions, active_seconds, book_opens, meaningful_reads, completions,
  calculation_version, refreshed_at, source_watermark
)
reporting.library_state_snapshot(
  snapshot_key CURRENT, saved_book_pairs, saved_books, saved_subject_keys,
  calculation_version, refreshed_at, source_watermark NULL
)
reporting.feed_daily(
  day, model_version, strategy, feed_path, population, platform,
  served_count, viewable_count, eligible_viewable_count,
  attributed_open_count, mature_attributed_open_count,
  attributed_read_10_count, attributed_read_50_count,
  attributed_complete_count, dwell_observations, dwell_seconds,
  skipped_visits, calculation_version, refreshed_at, source_watermark
)
reporting.pipeline_snapshot(
  collected_at, service, job_status, phase, status_count, oldest_pending_age_seconds,
  failure_count, throughput, calculation_version
)
reporting.projection_health(
  projection_name PK, last_successful_refresh, source_watermark,
  record_count, definition_version, refresh_status, last_error_code
)
\`\`\`

Isto é **contrato lógico**, não SQL Flyway pronto. Definir tipos, chaves, retenção, índices, unicidade e políticas de acesso no projeto de implementação. Não materializar \`subject_pseudonym\` em outputs do Grafana/SPA; conservar referência opaca apenas no processo de atribuição protegido. Agregados públicos internos usam limiar de divulgação por amostra; por padrão, **suprimir segmentos com menos de 20 leitores** e tornar limiar configurável/validado com segurança.

## Dicionário de métricas (definições rigorosas)

| Métrica | Unidade; fórmula ou critério de contagem | Denominador / fonte / condições |
|---|---|---|
| \`users_active_daily\` | identidades técnicas únicas (issuer + subject) com evento significativo no dia local | behavior events; excluir identidades incompletas; excluir bot/seed/teste conforme classificação de população |
| \`users_active_weekly/monthly\` | distinct subjects em janelas móveis de 7/30 dias | não somar DAUs; população e timezone iguais |
| \`new_reader_count\` | sujeitos cujo primeiro evento significativo ocorreu no período | não confundir registro Keycloak com uso |
| \`retention_D1/D7/D30\` | identidades técnicas (issuer + subject) do cohort de primeiro uso que retornaram exatamente no dia n / tamanho do cohort maduro | join e denominador usam o par issuer/subject; ausentes/unmatured = indisponíveis, não 0 |
| \`book_open_count\` | aberturas válidas deduplicadas por evento/ação | separar orgânico e feed; não somar eventos duplicados client + server |
| \`reading_active_seconds\` | soma dos segundos em sessões finais válidas, capped e anti-background | origem \`reader_state.session\`, controle de duplicações e timezone |
| \`meaningful_read_count\` | unidades únicas com leitura >= limiar configurado, **proposto 60s ativos** | somente se sessões e livro bem relacionados; versionar o threshold |
| \`read_10/read_50\` | primeiro cruzamento de 10%/50% por par user-book em janela | máximo de progresso, não número de \`READ_PROGRESS\` |
| \`book_completion_count\` | primeira conclusão válida por user-book dentro do cohort analisado | \`BOOK_COMPLETE\` ou regra versionada de progresso só após validação |
| \`feed_requests\` | requests completados por caminho/result | \`bookrush.feed.requests\` (Prometheus), não usuários únicos |
| \`feed_items_served\` | itens retornados ao consumidor | contador servidos/ledger itens; **não** viewable |
| \`viewable_impressions\` | impression IDs únicos com viewable confirmado e janela válida | \`recommendation.impression.viewable\` reconciliado com behavior |
| \`repeat_viewable_rate_30d\` | impressões viewable com impressão viewable anterior do mesmo leitor/livro nos 30 dias / impressões viewable | ledger de impression; identidade só é usada dentro da agregação protegida e nunca é exportada |
| \`viewable_unique_books\` | livros distintos com ao menos uma impressão viewable por dia/modelo/estratégia/caminho/plataforma | não representa catálogo elegível nem candidate pool |
| \`feed_ctr_viewable\` | impressões viewable maduras com ao menos um open atribuído à impressão / impressões viewable maduras | maturidade e janela de 7d iniciam em \`viewable_at\`; \`eligible_viewable_count\` e \`mature_attributed_open_count\` usam a mesma coorte |
| \`feed_meaningful_read_rate\` | viewable impressions com leitura significativa atribuída / viewable impressions elegíveis | mesmo vínculo, threshold versionado |
| \`feed_completion_rate\` | viewable impressions com conclusão atribuída / viewable impressions elegíveis e maduras | janela de observação >= janela de conclusão, mostrar maturaçao |
| \`excerpt_dwell_seconds\` | soma do maior intervalo válido de dwell por visita (visitId), em cada impressão | \`DWELL\` e \`EXCERPT_DWELL\` aceitos; durações inválidas são excluídas; legado sem visitId usa event ID |
| \`skipped_visits\` | visitas com evento explícito \`EXCERPT_SKIP\`, deduplicadas por visitId | exige exposição correlacionada e visitId; não inferir skip pela ausência de click; taxa requer denominador elegível |
| \`cold_start_rate\` | requests com fallback cold-start / requests de feed elegíveis | \`FeedMetrics\` + ledger, separando indisponibilidade do behavior |
| \`personalized_serving_rate\` | requests servidos por PFV1 / requests elegíveis | modo ON/SHADOW/OFF e rollout, não contar SHADOW como servido |
| \`shadow_overlap\` | items coincidentes entre controle e shadow / comparados | reportar população \`compared\`, sem claims de uplift |
| \`excerpt_coverage\` | livros elegíveis com excerpt validado / livros elegíveis para feed | snapshot consistente de catálogo + analytics |
| \`pipeline_success_rate\` | itens concluídos com sucesso / itens **terminais** do job | retries são tentativas, não novos itens |
| \`pipeline_queue_age\` | idade do item PENDING mais antigo, seconds | snapshot por worker, omitir quando scheduler intencionalmente disabled |
| \`outbox_delivery_lag_seconds\` | published_at − created_at por evento enviado | \`behavior.outbox\`, separar pendentes e enviados |
| \`event_rejection_rate\` | eventos rejeitados / eventos classificados (accepted + rejected persistidos) | representa apenas registros com resultado persistido; não estima tentativas perdidas antes de gravar |
| \`http_error_rate\` | HTTP 5xx / requests válidos | route templated, excluir health de SLIs quando especificado |
| \`http_request_p95\` | \`histogram_quantile(0.95, sum(rate(..._bucket[5m])) by (le,...))\` | somente histogramas com buckets; não média de p95 individuais |
| \`service_availability\` | \`successful eligible requests/eligible requests\` (SLI) | definir disponibilidade via probe/traffic, metas por serviço futuramente |
| \`data_freshness_seconds\` | agora UTC − \`source_watermark\` publicado | atraso real de ingestão; não confundir com hora da consulta |

**Attribution v1 (proposta):** elegibilidade condicionada a \`impression_id\` OU \`recommendation_item_id\` autenticado/correlacionado, subject e book iguais, ocorrido após \`first_viewable_at\` e antes de \`first_viewable_at + 7d\`. Se múltiplos exposures do mesmo par user-book forem elegíveis, aplicar **last viewable touch** determinístico, tie-break por impression_id; um outcome pode ser atribuído a no máximo uma exposição. Estado \`unattributed\`, \`organic\`, \`invalid_reference\`, \`outside_window\` explícito. Informar versão \`attribution-v1\`. Esse modelo é **analítico**, não efeito causal. Incluir janelas menores configuradas quando aprovadas.

## Catálogo de tags e cardinalidade

**Permitidas em Prometheus**: \`service\`, \`environment\`, \`method\`, \`route_template\`, \`status_class\`, \`phase\` (enum), \`outcome\` (enum), \`strategy\` (enum), \`feed_path\` (enum), \`reason\` (enum allow-listed), \`model_family\` (lista limitada), \`language\` (código suportado). Monitorar budget de séries.

**Proibidas em métricas/logs indiscriminados**: \`user_id\`, \`subject\`, \`book_id\`, \`excerpt_id\`, \`impression_id\`, \`request_id\`, \`job_id\`, email, IP completo, URL assinada, bearer token, texto/trecho, vetores e payloads sem sanitização. CorrelationId, traceId podem existir como campos de logs com retenção/ACL e como exemplar, **não como label**. Valores arbitrários de \`reason\`, \`error_code\`, path ou user-agent devem ser normalizados.

## Contrato de API reporting (implementado no admin-service)

Prefixo implementado: \`GET /api/admin/v1/reporting/*\`, protegido por OIDC do realm administrativo e pelas roles de reporting no backend. Nenhum endpoint recebe SQL/PromQL arbitrário. Os endpoints de maior cardinalidade aceitam \`page\` e \`limit\`; a consulta usa \`LIMIT limit + 1\` e \`OFFSET\` para produzir \`hasNext\` sem carregar páginas anteriores.

O facade calcula \`freshnessSeconds\` a partir do \`sourceWatermark\`. Quando a idade excede \`BOOKRUSH_REPORTING_STALE_AFTER_SECONDS\` (padrão de produção: 900 segundos), o estado é \`STALE\`; timestamps JDBC são normalizados para ISO-8601 e o response inclui \`warnings\` sem ocultar a população não classificada.

\`\`\`json
{
  "schemaVersion": "dashboard-response-v1",
  "panelId": "FD-02",
  "metricId": "feed_ctr_viewable",
  "definitionVersion": "attribution-v1",
  "status": "OK",
  "unit": "ratio",
  "timezone": "America/Sao_Paulo",
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-11-01T00:00:00Z",
  "population": "PRODUCT",
  "dimensions": {"strategy": "PERSONALIZED"},
  "sourceWatermark": "2026-11-01T00:00:00Z",
  "refreshedAt": "2026-11-01T00:10:00Z",
  "numerator": 152,
  "denominator": 1000,
  "value": 0.152,
  "sampleSize": 1000,
  "series": [],
  "warnings": [],
  "page": 0,
  "pageSize": 50,
  "hasNext": false
}
\`\`\`

**Exemplo sintético de contrato, NÃO dado real.** \`status\`: \`OK|NO_DATA|NOT_INSTRUMENTED|STALE|RESTRICTED|ERROR|INSUFFICIENT_SAMPLE|UNKNOWN_FRESHNESS\`. Ausência de atividade é \`NO_DATA\`; falha/atraso de atualização é informado separadamente da última atividade de negócio. A API aplica range máximo de 366 dias, limites de amostra/página e autorização de reporting no backend; OpenAPI versionado é parte da implementação. Cache/rate limit permanecem controles de borda a validar no gateway antes do fechamento do pacote.

## Integridade, privacidade e qualidade da medição

- Identidade técnica deve seguir contrato \`TechnicalIdentity(issuer, subject)\` do PFV1. Não expor a identidade em tabelas ou logs de dashboard.
- Segregar \`PRODUCT\`, \`ADMIN_SEED\`, \`TEST\` e \`UNKNOWN\` por origem autenticada; default da visão business é somente PRODUCT. Eventos históricos sem origem recuperável permanecem UNKNOWN.
- \`identity_issuer\` e \`identity_subject\` não podem ser aceitos como autoridade de payload cliente. Validar principal no servidor e eventKey/idempotência.
- Conferir \`BOOK_OPEN\` gerado por frontend e reader state: um único fato lógico (mesma chave ou dedup por mecanismo versionado), não simplesmente remover registros retroativamente sem relatório de reconciliação.
- Atributos de impressões e outcomes V10 são **existentes**, mas sua completude precisa de testes integrados PFV1 com usuários distintos, livros distintos, múltiplas exposições e devoluções.
- Validar \`READ_SESSION\` associado ao livro antes de somar tempo por obra; \`session_id\` deve ser único e encerramento idempotente.
- Não usar \`behavior.daily_book\` para CTR atribuída: agregados por livro não preservam o mesmo cohort/exposure.
- Prever exclusão/retificação de dados pessoais e políticas de retenção LGPD; documentar base legal e consentimento aplicável ao tracking. Limitar exportação por role, auditar quem consultou/exportou e evitar pequenos grupos reidentificáveis.
- Evidência de dashboards: screenshot + JSON de consulta sem PII + SQL/PromQL versionado + testes de fixture + lista de versões + commit/imagem. O gráfico deve explicitar período, timezone, atualização, \`n\` e definição.
- Dados históricos cuja origem autenticada não é recuperável permanecem \`UNKNOWN\`; novas classificações usam origem autenticada e não campos arbitrários enviados no evento.
- PL-07 usa \`reporting.excerpt_production_snapshot\`; estados são \`RANKED\`, \`ELIGIBLE_UNRANKED\`, \`RANKING_EXCLUDED\` ou \`STRUCTURALLY_EXCLUDED\`. Motivos são whitelisted e agregados. Analytics não mantém aprovação/publicação editorial de excerpts, então esses rótulos não são sintetizados.
- PL-10 usa \`reporting.import_quality_daily\`; retry conta tentativas cujo \`attempt_number > 1\`, erros são agrupados em buckets limitados, direitos representam a decisão no instante do refresh e asset é a última versão vinculada à tentativa. O domínio não fornece flag autoritativa de duplicação; retries não substituem essa medida.
- PL-11 usa \`reporting.backlog_snapshot\` para ingestion task, analytics job item e health de projeções. Worker stuck significa RUNNING acima de 15 minutos, sem constituir SLA de negócio. Frescor é idade do último refresh bem-sucedido; não há prazo de publicação inferido.
- SO-05 usa \`reporting.editorial_submission_funnel_daily\`, agrupado por \`publisher.submission.created_at\`. Cada etapa conta submissões distintas que já alcançaram revisão, aprovação, publicação ou publicação ativa no último refresh; direitos são uma dimensão paralela. Coortes recentes podem estar em andamento, então o painel não apresenta taxas de conversão nem SLA sem uma regra de maturidade aprovada.
- SO-07 usa \`reporting.rights_status_daily\`: decisões do catálogo e atestações de publisher permanecem em \`source_type\` separados; território e tipo do direito são dimensões, e estados ACTIVE/EXPIRED/NOT_YET_VALID/REVOKED derivam das decisões registradas e da janela temporal atual. Atestação significa apenas declaração registrada, não decisão do catálogo nem validação jurídica. A resposta omite evidência, ator e subject.
- SO-01 usa as colunas \`behavior_*\` de \`reporting.social_daily\`, derivadas apenas de eventos aceitos BOOK_LIKE/UNLIKE/SAVE/UNSAVE/SHARE/COMMENT. Chaves únicas da fonte impedem duplicatas aceitas; distinct actors/books são métricas separadas. Os contadores legados de \`social.activity\` não são somados aos eventos behavior.
- SO-02/GR-10 conta leitores distintos somente em eventos de abertura/leitura (BOOK_OPEN, READ_SESSION, READ_PROGRESS, BOOK_COMPLETE, READ_COMPLETE); likes, saves, shares e comentários entram apenas na medida separada de interações e não aumentam leitores.
- GR-06 agrupa cada par por sua primeira sessão vinculada à obra, usando o primeiro READ_PROGRESS como fallback. As colunas 10/50/100 contam pares que já cruzaram cada marco pela primeira vez, a partir de todo o histórico retido. Progresso repetido não duplica pares; o painel mostra contagens alcançadas sem taxa de coortes imaturas. A projeção não guarda IDs.
- Recommendation reporting recebe somente a categoria allowlisted `web`/`mobile` encaminhada pelo Reader BFF; valores ausentes/inválidos ficam `UNKNOWN` e User-Agent não é persistido. FD-12 guarda somente buckets categóricos no contexto do request e publica contagens, sem valores de perfil ou sujeitos.
