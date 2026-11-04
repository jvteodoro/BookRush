# ADR / especificação funcional — Dashboards administrativos e observabilidade

Status: PROPOSTO • Issue [#8](https://github.com/jvteodoro/BookRush/issues/8) • 2026-11-04

## 1. Problema e resultado esperado

A console administrativa atual possui Overview com contadores básicos e uma página Users & moderation. Não possui uma superfície específica para analisar saúde da plataforma, desempenho de pipelines, crescimento, engajamento nem efetividade de recomendações. O objetivo é construir a seção **Dashboards**, com dados verificáveis, filtros, navegação para detalhes e ligação contextual a Grafana e runbooks. Não confundir a entrega deste módulo React com a implantação da infraestrutura de coleta.

**Dois planos, uma experiência:**
1. **Operations:** infraestrutura, serviços e dependências, erros/latência/traces, ingestão, analytics, Feed, IAM, CI/CD e qualidade da telemetria.
2. **Business:** aquisição e retenção, funil excerpt→livro, tempo de leitura, qualidade de recomendação, catálogo e oferta editorial, publishers, segurança/moderação e experimentos.

Todos os números exibidos precisam de origem, método de cálculo, unidade, timezone, período, população, data de atualização e estado de qualidade. Nenhum placeholder numérico será interpretado como dado real.

## 2. Leitura do código existente (ponto de partida, não comprovação de implantação)

- UI: admin-web/src/app/Dashboard.tsx, admin-web/src/app/navigation.ts, admin-web/src/features/operations/OperationsPage.tsx, admin-web/src/lib/services.ts.
- Instrumentação: platform/bookrush-observability-spring-boot-starter; services/book-analytics-service/.../AnalyticsMetrics.java; services/book-ingestion-service/.../IngestionMetrics.java; services/recommendation-service/.../FeedMetrics.java.
- Fatos: behavior.events / daily_book / daily_user_book / outbox / rejects; recommendation.request / item / impression; reader_state.session / reading_day / progress; analytics.analysis_job / analysis_job_item / analysis_phase_timing; publisher.*; catalog.*.
- Frontend: frontend/src/telemetry.ts, mobile/src/telemetry.ts, frontend/src/reader/LiveReader.tsx e frontend/src/main.tsx.
- Infra: infrastructure/compose.yaml, Traefik, PostgreSQL, Redis, SeaweedFS, Keycloak, Jenkins.
- Gaps a validar: não foi identificado no snapshot da revisão um stack completo Grafana/Prometheus/Loki/Tempo provisionado; exposição Prometheus não uniforme; correlação HTTP não equivale a tracing distribuído; divergências de eventos comprometem denominadores de negócio.

**Correções bloqueantes de qualidade:** verificar e eliminar emissão duplicada de BOOK_OPEN (frontend e reader-state); impressão viewable do recommendation-service com bookId ausente no evento behavior; READ_SESSION sem bookId em agregação que o exige; rebuilds divergentes de BOOK_UNLIKE; preservar contexto de exposição/excerpt/modelo para atribuição. Conferir o estado da main no momento da implementação, pois o projeto evolui continuamente.

## 3. Design de navegação e UX

Criar item **Dashboards** no menu da Admin Web; subrotas:
- /dashboards/overview — cockpit resumido, 6–10 KPIs e links para detalhe;
- /dashboards/operations — serviços, infraestrutura, segurança e SLO;
- /dashboards/pipelines — ingestão, processamento, distribuição, workers e filas;
- /dashboards/business — crescimento, comportamento e funil;
- /dashboards/recommendations — modelos, feed, excerpts e experimentos;
- /dashboards/content — corpus, catálogo, editoria, publishers;
- /dashboards/data-quality — integridade, lag, contratos e cobertura.

Não repetir funcionalidades mutáveis já presentes em Catalog, Review Queue, Analytics, Training ou Users & moderation: dashboards são leitura e diagnóstico; ações de operar/moderar navegam ao módulo proprietário. Dar acesso contextual às dashboards Grafana (deep links assinados/configurados, sem credenciais em URLs), sem exigir iframe de aplicações completas como solução padrão.

Filtros globais sincronizados na URL: intervalo e comparação anterior, granularidade, timezone (padrão UTC em storage; exibição America/Sao_Paulo), ambiente, serviço (Operations), plataforma web/mobile, população END_USER/ADMIN_SEED/ALL, idioma, país **somente se coletado legitimamente**, strategy/modelVersion, book/excerpt (onde aplicável). Filtros indisponíveis precisam de marcação explícita. Não aplicar filtros que não existam nas fontes.

UX: gráficos responsivos desktop/mobile, legenda, tooltip com fórmula e unidade, tabela acessível para detalhes, export CSV com filtros e timestamp, refresh manual, auto-refresh configurável apenas para painéis operacionais, estados loading/error/empty/stale/unavailable distintos e navegação por teclado. Presets: 1h/24h/7d/30d/90d e data customizada; business diário/semanal, operations 5m/1h. Defaults de visualização nunca fingem precisão de tempo real.

## 4. Catálogo de painéis obrigatório

Cada série e gráfico tem definição completa no [catálogo de métricas](./dashboard-metrics-catalog.md).

**Operations overview:** disponibilidade por serviço (stat/status history), taxa de erro HTTP (timeseries), taxa de requisições (timeseries), P50/P95/P99 (timeseries), CPU/RAM/disco (timeseries/gauge), restart/OOM (bars), dependências downstream e latências (heatmap), alertas ativos (table), deploy markers em métricas (annotations).

**Reliability/Infrastructure:** PostgreSQL conexões/locks/slow queries/pool; Redis memória/evictions/hit rate; SeaweedFS S3 erro/latência/capacidade; Keycloak falhas autenticação/token latency; Traefik 4xx/5xx e upstream; Docker host/cAdvisor; Jenkins build/deploy duration/failure/rollback.

**Pipelines:** jobs por status, throughput books/h, distribuição de tempo por fase, backlog e oldest age, retry/recovery de lease, extrapolação de ETA apenas marcada como estimativa, custódia de objetos e integridade de versões; erro por classe; analytics runtime latência BGE-M3/NLI/spaCy/fastText, model availability, chamadas e memória; outbox pending/oldest lag/retries/dead-letter equivalente.

**Business:** DAU/WAU/MAU, novos/recorrentes, retenção D1/D7/D30, sessões ativas, minutos de leitura, livros iniciados/concluídos, funil com denominadores coerentes, CTR viewable→open, conversão open→leitura, dwell percentiles, shares/saves/likes, frequência e taxa de retorno. Exibir separação por END_USER/ADMIN_SEED e plataforma. Sem eventos confiáveis, marcar N/A e vincular lacuna.

**Recommendations & ML:** request throughput; feed load latency, candidates retrieved/scored/selected, distribuição de strategies, attach/fallback/cache/circuit breaker; cobertura elegível; CTR/reading conversion por model_version/strategy com intervalo de confiança e mínimo de amostra; ranking position curves; diversidade e novidades; recência; modelo exposto x modelo comparado; A/B randomizado só quando realmente implementado. Distinguir métrica descritiva de efeito causal.

**Content & editorial:** funil descoberta→importação→direitos→review→publicação→analytics→feed eligible; acervo por idioma/licença/origem; processamento e taxa de erro; candidatos por livro, feature coverage/unknown; aprovação/revogação com histórico; tempo entre etapas, rejeições e motivos; métricas agregadas de publisher somente com autorização adequada.

**Data quality:** events received/accepted/rejected/duplicate, taxa de missing identity/bookId/impressionId/excerptId, outbox lag, freshness das projeções, cobertura de eventos em relação ao ledger, drift entre recomputação e agregação, eventos fora de ordem/futuro, schema versions e degradações.

## 5. Limites arquiteturais

1. Grafana é **plataforma observability**: Prometheus (métricas técnicas), Loki (logs), Tempo (traces), Alloy/OTel (coleta). Admin Web **não** consulta essas fontes diretamente do navegador.
2. Métricas de negócio são **fatos e agregados persistentes** derivados de eventos auditáveis, não séries Prometheus com tags userId, bookId, excerptId, subject, jobId, email, traceId ou URL dinâmica.
3. Criar um **read API** versionado para dashboard (ou facade no admin-service com client S2S dedicado) que consulta projeções read-only com ownership claro, sem permitir leitura SQL arbitrária ou acesso cross-schema transacional da UI. Para o Grafana, provisionar datasource PostgreSQL com role somente SELECT em reporting views e recursos consumíveis com RBAC.
4. Não duplicar domain writers nem misturar o schema de origem com reporting. Views/materialized views ou ETL incremental, com controle de refresh, watermark e reconciliação, podem alimentar reporting; registrar a proveniência e versão da definição.
5. Correlação/trace propagado via W3C; logs sem PII/token/trecho de livro/corpo de request/URL S3 assinada. Conectar trace de front para BFF apenas quando tecnicamente seguro e sem expor token.
6. Respeitar os realms Keycloak: ADMIN_OPERATOR para operations, PRODUCT_ANALYST para métricas agregadas, ADMIN_ADMIN/SECURITY para segurança sensível; mapear grupos existentes ou criar via IaC com menor privilégio. Nenhum ID opaco de usuário aparece em métricas agregadas, alertas ou logs.
7. Janela/coorte e fuso determinam corte; usar UTC na persistência e calendário explícito em análise. Sem dupla contagem de múltiplos eventos e múltiplas impressões de um mesmo request.

## 6. Contrato da read API

Propor prefixo /api/admin/v1/dashboards (autorizado), endpoints com respostas tipadas:
- GET /catalog — lista de metricIds, definições, filtros, unidades e disponibilidade;
- GET /summary?from=&to=&population=&platform=;
- GET /timeseries?metricIds=&from=&to=&grain=...;
- GET /breakdown?metricId=&dimension=&limit=&cursor=...;
- GET /funnel?name=excerpt_to_read&from=&to=...;
- GET /cohorts?metric=retention&cohortGrain=week...;
- GET /quality — freshness, completeness, watermark e incidentes;
- GET /links — URLs de dashboards Grafana permitidos por role.

Responses: {metricId, definitionVersion, unit, timezone, interval:{from,to}, filters, calculatedAt, watermark, freshnessSeconds, status: OK|PARTIAL|STALE|UNAVAILABLE|INSUFFICIENT_SAMPLE, sampleSize, source, points:[{t,value}], qualityWarnings:[]}. Payloads de breakdown/funnel/cohorts devem ser estruturados e paginados; sem strings de SQL fornecidas por cliente. Limites: <=90 dias granularidade horária, <=2 anos diária (ajustáveis por capacidade), pageSize <=100, ordenação allowlist, timeouts e cache HTTP controlado. Endpoints não exigem acesso direto de admin-web a dashboards protegidos do Grafana.

## 7. Experimentos, capacidade e riscos

- Benchmark de consultas usando EXPLAIN ANALYZE em fixtures representativos, não query livre do Grafana no transacional.
- Índices e views com migrações controladas, sem alterar migrations Flyway históricas. Processo determinístico de backfill e reconciliação.
- Observabilidade da própria camada de observabilidade: targets UP, scrape failure, scrape lag, Loki ingest errors, Tempo export drop, WAL/volume, datasource health, dashboard load errors.
- Controle de cardinalidade por cardinality budget e allowlist de labels; limites de retenção configuráveis por volume/disco; provisionar backups para dashboards e rule definitions.
- Não prometer ganhos de modelo sem validação; experimentos exigem assignment estável, exposure ledger, outcome window e análise por intenção de tratar.
- Escalar depois: manter Prometheus OSS e PostgreSQL inicial, avaliar Mimir/OLAP apenas após evidências reais de volume.

## 8. Implementação e aceite

Plano de execução, proprietários, dependências, riscos e critérios completos: [delivery-plan](./dashboard-delivery-plan.md). Os IDs e fórmulas verificáveis de cada indicador estão no [metric catalog](./dashboard-metrics-catalog.md).

**Não considerar entregue** um dashboard com mock, endpoint de health isolado, gráfico sem denominador ou queries sem permissão; exigir prova E2E com evento→persistência→agregado→API→gráfico, e alerta técnico→trace→logs. A issue #8 é épico funcional, não um único componente visual.
