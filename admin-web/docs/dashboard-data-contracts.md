# Contratos de indicadores e fontes — dashboards da administração

> **Estado:** contrato proposto para issue #8; o inventário de fontes abaixo foi conferido no código em 2026-11-04, porém **não** implica que views, APIs ou exporters já existam. Mudanças de métricas exigem versão e atualização conjunta dos painéis, testes e documentação.

## Ownership, linhagem e classificação

| Domínio | Origem autoritativa existente | Relação com reporting | Instrumentação |
|---|---|---|---|
| Behavior | \`behavior.events\`, \`behavior.rejects\`, \`behavior.outbox\`, \`behavior.daily_book\`, \`behavior.user_book_signal\`, \`behavior.outcome_attribution\` (V10) | projeção append-only/deduplicada e facts de outcomes; **não** usar \`daily_book\` como prova de atribuição por impression | existente parcial / necessidades P1 |
| Recommendation | \`recommendation.request\`, \`recommendation.item\`, \`recommendation.impression\` (\`viewable\`, \`rank\`, versões/razões) | cópias de leitura/cohort em reporting com cutoffs coerentes | PFV1 e FeedMetrics parcialmente instrumentados |
| Reader-state | \`reader_state.session\` (book e correlation V7/V8), \`reader_state.progress\`, \`reading_day\`, \`library\` | fatos de leitura e atividade; sessão sem livro ≠ tempo por livro | SQL existente; telemetria contextual a auditar |
| Catalog / Ingestion | direitos, status, assets e versões; jobs de ingestão; arquivos no SeaweedFS | snapshots de disponibilidade e pipeline; não acessar schema operacional no browser | IngestionMetrics e health parcial |
| Content Analytics | \`analytics.analysis_job\`, \`analysis_job_item\`, \`analysis_phase_timing\`, \`analysis_phase_log\`, excerpts e rank/versionamentos | snapshots batch e cobertura por versão; comparação de fases por tentativa | AnalyticsMetrics já presente |
| Publisher / Social / Admin | submission/review/publication, social interactions, denúncias/moderação | projeções agregadas com autorização restrita | apenas parte das métricas persistidas |
| Host e serviços | Actuator, \`FeedMetrics\`, containers, Keycloak, Jenkins, Traefik | Prometheus/Loki/Tempo + exporters (a construir) | instrumentação desigual; verificar endpoints |
| Experimentos | não considerar experimentos causalmente medidos sem assignment/holdout persistentes | schema/projeção proposta; desenho e versionamento | **não instrumentado** |

Snapshots cross-context devem ser gerados por jobs de leitura/autorização explícita, idealmente exports com manifesto \`schemaVersion\`, \`cutoff\`, \`highWatermark\`, \`rowCount\`, \`sha256\` e timestamps. O runtime de produção de um microsserviço não executará SELECT cross-schema de outro owner; a facade reporting lê **somente** projeções próprias. ACL e segregação de roles de exportação são obrigatórias.

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
reporting.feed_daily(
  day, model_version, strategy, feed_path, population, platform,
  served_count, viewable_count, attributed_open_count,
  attributed_read_10_count, attributed_read_50_count,
  attributed_complete_count, dwell_observations, dwell_seconds,
  calculation_version, refreshed_at, source_watermark
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
| \`users_active_daily\` | sujeitos únicos com evento significativo no dia local | activity fact; excluir bot, seed, teste por default; ações em allow-list versionada |
| \`users_active_weekly/monthly\` | distinct subjects em janelas móveis de 7/30 dias | não somar DAUs; população e timezone iguais |
| \`new_reader_count\` | sujeitos cujo primeiro evento significativo ocorreu no período | não confundir registro Keycloak com uso |
| \`retention_D1/D7/D30\` | leitores do cohort de primeiro uso que retornaram exatamente no dia n / tamanho do cohort maduro | ausentes/unmatured = indisponíveis, não 0 |
| \`book_open_count\` | aberturas válidas deduplicadas por evento/ação | separar orgânico e feed; não somar eventos duplicados client + server |
| \`reading_active_seconds\` | soma dos segundos em sessões finais válidas, capped e anti-background | origem \`reader_state.session\`, controle de duplicações e timezone |
| \`meaningful_read_count\` | unidades únicas com leitura >= limiar configurado, **proposto 60s ativos** | somente se sessões e livro bem relacionados; versionar o threshold |
| \`read_10/read_50\` | primeiro cruzamento de 10%/50% por par user-book em janela | máximo de progresso, não número de \`READ_PROGRESS\` |
| \`book_completion_count\` | primeira conclusão válida por user-book dentro do cohort analisado | \`BOOK_COMPLETE\` ou regra versionada de progresso só após validação |
| \`feed_requests\` | requests completados por caminho/result | \`bookrush.feed.requests\` (Prometheus), não usuários únicos |
| \`feed_items_served\` | itens retornados ao consumidor | contador servidos/ledger itens; **não** viewable |
| \`viewable_impressions\` | impression IDs únicos com viewable confirmado e janela válida | \`recommendation.impression.viewable\` reconciliado com behavior |
| \`feed_ctr_viewable\` | impressões viewable elegíveis com primeiro open atribuído / impressões viewable elegíveis | atribuição 7d **proposta**, mesma coorte/cutoff/popu. |
| \`feed_meaningful_read_rate\` | viewable impressions com leitura significativa atribuída / viewable impressions elegíveis | mesmo vínculo, threshold versionado |
| \`feed_completion_rate\` | viewable impressions com conclusão atribuída / viewable impressions elegíveis e maduras | janela de observação >= janela de conclusão, mostrar maturaçao |
| \`excerpt_dwell_seconds\` | tempo de exposição ativa validada e deduplicada por impression | \`DWELL\` vs \`EXCERPT_DWELL\` normalizar; inicialmente “not instrumented” se inválido |
| \`skip_rate\` | impressões elegíveis puladas após exposição mínima / impressões elegíveis | requer evento dedicado / regra versionada, **não inferir de ausência de click** |
| \`cold_start_rate\` | requests com fallback cold-start / requests de feed elegíveis | \`FeedMetrics\` + ledger, separando indisponibilidade do behavior |
| \`personalized_serving_rate\` | requests servidos por PFV1 / requests elegíveis | modo ON/SHADOW/OFF e rollout, não contar SHADOW como servido |
| \`shadow_overlap\` | items coincidentes entre controle e shadow / comparados | reportar população \`compared\`, sem claims de uplift |
| \`excerpt_coverage\` | livros elegíveis com excerpt validado / livros elegíveis para feed | snapshot consistente de catálogo + analytics |
| \`pipeline_success_rate\` | itens concluídos com sucesso / itens **terminais** do job | retries são tentativas, não novos itens |
| \`pipeline_queue_age\` | idade do item PENDING mais antigo, seconds | snapshot por worker, omitir quando scheduler intencionalmente disabled |
| \`outbox_delivery_lag_seconds\` | published_at − created_at por evento enviado | \`behavior.outbox\`, separar pendentes e enviados |
| \`event_rejection_rate\` | eventos rejeitados / eventos recebidos **com status identificável** | precisa instrumentar attempted count, não dividir rejects por accepted apenas |
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

**Exemplo sintético de contrato, NÃO dado real.** \`status\`: \`OK|NO_DATA|NOT_INSTRUMENTED|STALE|RESTRICTED|ERROR|INSUFFICIENT_SAMPLE|UNTRUSTED\`. Para status diferente de OK, \`value=null\` e \`numerator/denominator\` podem ser nulos. Erros não revelam SQL interno ou PII. A API aplica range máximo de 366 dias, limites de amostra/página e autorização de reporting no backend; OpenAPI versionado é parte da implementação. Cache/rate limit permanecem controles de borda a validar no gateway antes do fechamento do pacote.

## Integridade, privacidade e qualidade da medição

- Identidade técnica deve seguir contrato \`TechnicalIdentity(issuer, subject)\` do PFV1. Não expor a identidade em tabelas ou logs de dashboard.
- Segregar \`PRODUCT\`, \`ADMIN_SEED\`, \`TEST\` e \`SYNTHETIC\` por provenance; default da visão business é somente PRODUCT. Ausência de tag populacional não pode ser presumida como PRODUCT.
- \`identity_issuer\` e \`identity_subject\` não podem ser aceitos como autoridade de payload cliente. Validar principal no servidor e eventKey/idempotência.
- Conferir \`BOOK_OPEN\` gerado por frontend e reader state: um único fato lógico (mesma chave ou dedup por mecanismo versionado), não simplesmente remover registros retroativamente sem relatório de reconciliação.
- Atributos de impressões e outcomes V10 são **existentes**, mas sua completude precisa de testes integrados PFV1 com usuários distintos, livros distintos, múltiplas exposições e devoluções.
- Validar \`READ_SESSION\` associado ao livro antes de somar tempo por obra; \`session_id\` deve ser único e encerramento idempotente.
- Não usar \`behavior.daily_book\` para CTR atribuída: agregados por livro não preservam o mesmo cohort/exposure.
- Prever exclusão/retificação de dados pessoais e políticas de retenção LGPD; documentar base legal e consentimento aplicável ao tracking. Limitar exportação por role, auditar quem consultou/exportou e evitar pequenos grupos reidentificáveis.
- Evidência de dashboards: screenshot + JSON de consulta sem PII + SQL/PromQL versionado + testes de fixture + lista de versões + commit/imagem. O gráfico deve explicitar período, timezone, atualização, \`n\` e definição.
