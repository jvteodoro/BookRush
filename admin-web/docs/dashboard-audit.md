# Auditoria inicial dos dashboards administrativos

Data da auditoria: 2026-11-04  
Base: `main` em `45645e4` + especificação do PR #60 incorporada na branch de
trabalho. O PR #60 não estava integrado à `main`; a documentação foi lida na
branch remota `origin/docs/admin-dashboards-observability-spec` em `413ded1`.

## Critério

`EXISTING` significa que há uma implementação do dado ou da instrumentação,
mas não necessariamente um painel funcional. `PARTIAL` significa que há parte
da fonte ou métrica, porém faltam contrato, projeção, segurança, visualização
ou validação integrada. `MISSING` significa que a capacidade não foi
encontrada. `VALIDATED` exige evidência de runtime correspondente ao painel;
nenhum painel recebe essa classificação nesta auditoria inicial. `BLOCKED`
fica reservado para dependência externa identificada.

## Matriz de rastreabilidade

| ID | Painel | Fonte autoritativa | Estado atual | Lacuna principal | Teste/evidência necessária |
|---|---|---|---|---|---|
| EX-01 | Visão geral de atividade | `behavior` + `reader_state` | PARTIAL | Não há projeção de atividade nem API | Fixture de atividade + snapshot |
| EX-02 | Funil principal | `/reporting/executive-summary` + `feed_daily` | PARTIAL | Funil agregado com denominadores implementado; falta gráfico dedicado e fixture de produção | Fixture viewable/open/read/complete |
| EX-03 | Saúde do produto | Prometheus + reporting | MISSING | Não existe stack nem facade | Scrape, freshness e autorização |
| EX-04 | Evolução de engajamento | `reader_state.session` | PARTIAL | Sessões não agregadas para reporting | SQL/projeção com timezone |
| EX-05 | Conteúdo disponível | Catalog + analytics | PARTIAL | Não há snapshot reader-ready/excerpt-ready | Reconciliação catalog/analytics |
| EX-06 | Comparação de período | `/reporting/executive-summary` | PARTIAL | Endpoint atual/precedente com janela igual implementado; falta delta visual dedicado | Teste de comparação temporal |
| OP-01 | Mapa de disponibilidade | Grafana `bookrush-operations` + projection health | PARTIAL | Link autenticado e tabela de freshness integrados no Admin Web; falta smoke do Grafana público | Prometheus targets + gateway smoke |
| OP-02 | Taxa de requisições | Micrometer HTTP | PARTIAL | Exposição/scrape não uniformes | Série HTTP real por rota |
| OP-03 | Erros HTTP | Micrometer HTTP | PARTIAL | Sem painel, regras ou error budget | Carga controlada 4xx/5xx |
| OP-04 | Latência por serviço | Micrometer timers | PARTIAL | Histogramas e buckets não padronizados | P50/P95/P99 agregáveis |
| OP-05 | JVM e containers | JVM/ Docker | PARTIAL | Sem cAdvisor/Prometheus provisionado | CPU, RSS, GC, restart e OOM |
| OP-06 | PostgreSQL | `pg_stat_*` | MISSING | Exporter e consultas controladas ausentes | PostgreSQL real + limites |
| OP-07 | Redis | Redis | MISSING | Exporter e fonte não encontrados | Hit ratio e eviction em runtime |
| OP-08 | SeaweedFS/S3 | SeaweedFS + storage metrics | PARTIAL | Health/storage existem, sem painel/exporter | Operação S3 e persistência |
| OP-09 | Gateway/Keycloak | Traefik + Keycloak | PARTIAL | Sem métricas de auth/gateway agregadas | 401/403/5xx e latência |
| OP-10 | Jenkins e releases | Jenkins API/build metadata | PARTIAL | Histórico não está em datasource provisionado | Build/deploy/digest real |
| OP-11 | Dependency tracing | OTel/Tempo | MISSING | Starter só fornece correlation ID | Trace BFF→recommendation→DB |
| OP-12 | Incidentes/SLO | Prometheus alert rules | MISSING | Sem regras, SLO ou runbooks integrados | Alerta controlado + recuperação |
| PL-01 | Ingestão | Ingestion jobs/metrics | PARTIAL | `IngestionMetrics` só conta processado/falho | Estados, throughput e retries |
| PL-02 | Fila de ingestão | Ingestion jobs | PARTIAL | Não há snapshot de fila/idade | Fixture pending/running/failed |
| PL-03 | Pipeline de analytics | `analytics.analysis_job/item` | PARTIAL | Dados persistem, sem API/painel | Contagens reais por estado |
| PL-04 | Tempo por fase | `analysis_phase_timing` | PARTIAL | Sem reporting de percentis por tentativa | P50/P95/P99 e retries |
| PL-05 | Erros de analytics | Phase log/error fields | PARTIAL | Sem projeção de stuck leases | Error code + recuperação |
| PL-06 | Infra ML | Runtime Python | MISSING | Sem métricas de modelo integradas | Disponibilidade/latência por modelo |
| PL-07 | Produção de excerpts | Analytics excerpts/ranks | PARTIAL | Fonte existe, sem snapshot editorial | Cobertura por idioma/livro |
| PL-08 | Cobertura de corpus | Analytics + catalog | PARTIAL | Sem projeção de cobertura | Direitos/reader-ready/score |
| PL-09 | Qualidade editorial | Review/annotations | MISSING | Sem painel consolidado de revisão | Fila, SLA e motivos |
| PL-10 | Qualidade de importação | Catalog/ingestion | PARTIAL | Proveniência existe, sem agregação | Duplicação/direitos/assets |
| PL-11 | Frescor e backlog | Jobs/watermarks | MISSING | Não há watermark de reporting | Backlog e SLA medidos |
| GR-01 | Usuários únicos | Behavior events | PARTIAL | Não há identidade analítica/projeção | DAU/WAU/MAU com população |
| GR-02 | Novos e recorrentes | Behavior events | MISSING | Primeiro uso não projetado | Fixture de coorte |
| GR-03 | Retenção cohorts | Activity facts | MISSING | Coortes e maturidade ausentes | D1/D7/D30 maduros |
| GR-04 | Engajamento de leitores | Reader sessions/events | PARTIAL | Tempo ativo ainda não consolidado | Sessões com cap/background |
| GR-05 | Distribuição de leitura | Reader sessions | MISSING | Sem agregação de duração | Histograma e percentis |
| GR-06 | Progresso | `reader_state.progress` | PARTIAL | Marcos e primeira passagem não projetados | 10/50/100 por leitor-livro |
| GR-07 | Biblioteca | `reader_state.library` | PARTIAL | Fonte existe, sem API analítica | Saves/unsaves e leitores únicos |
| GR-08 | Streaks e hábitos | `reading_day` | PARTIAL | Fonte existe, sem painel/projeção | Calendário e distribuição |
| GR-09 | Web x Mobile | Client telemetry | MISSING | Plataforma não é dimensão confiável | Telemetria versionada |
| GR-10 | Top books e idioma | `reporting.book_engagement` | PARTIAL | Projeção por obra/idioma e limiar `minReaders` implementados; falta visualização de tendência dedicada | Agregação com n mínimo |
| FD-01 | Funil de recomendação | Recommendation ledger + outcomes | PARTIAL | Ledger existe, outcomes não reconciliados | Atribuição versionada |
| FD-02 | CTR viewable→open | Impressions/behavior | PARTIAL | Viewability e open ainda precisam reconciliação | Numerador/denominador fixture |
| FD-03 | Dwell de excerpt | Frontend telemetry | PARTIAL | Normalização/deduplicação não comprovadas | Dwell ativo e skip explícito |
| FD-04 | Abertura/leitura | Reader progress + attribution | PARTIAL | Janela e vínculo de leitura incompletos | 10/50/complete atribuído |
| FD-05 | Comparação de estratégia | Recommendation request | PARTIAL | Sem snapshot/reporting por estratégia | Coortes não causais |
| FD-06 | PFV1 caminhos | `FeedMetrics` + ledger | PARTIAL | Métrica existe, sem Prometheus/dashboard | ON/OFF/SHADOW e fallback |
| FD-07 | Performance do ranking | `FeedMetrics` timers | PARTIAL | Sem scrape e percentis operacionais | Latência por etapa real |
| FD-08 | Coverage/diversidade | `FeedMetrics` + Grafana | PARTIAL | Candidatos/itens exportados como métricas; repetição e cobertura histórica ainda não projetadas | Candidatos, repetição e cobertura |
| FD-09 | Relevância por posição | Impression/item + outcomes | MISSING | Outcome por rank não projetado | Heatmap sem PII |
| FD-10 | Experimentos | Assignment persistido | MISSING | Não há experimento causal implementado | Assignment/guardrails |
| FD-11 | Shadow overlap | `FeedMetrics` + Grafana | PARTIAL | Overlap, comparação e movimento agora têm séries operacionais; assignment persistido ainda não está no reporting | Comparação e amostra |
| FD-12 | Qualidade do perfil | `FeedMetrics` + Grafana | PARTIAL | Buckets de confiança e sinais exportados; snapshot histórico do perfil ainda pendente | Coverage/confidence buckets |
| FD-13 | Livro/excerpt | Excerpt ledger/outcomes | MISSING | Performance por excerpt não autorizada/projetada | Drill-down com controle de acesso |
| SO-01 | Interações sociais | Social/behavior | PARTIAL | Dados existem, sem fatos analíticos | Likes/saves/shares/comments |
| SO-02 | Engajamento de obras | `reporting.book_engagement` | PARTIAL | Join governado entre catálogo, eventos e agregados; publisher-scope ainda não exposto | n mínimo e deduplicação |
| SO-03 | Moderação | Admin reports | PARTIAL | Endpoint existe, sem série/SLA | Estados e aging |
| SO-04 | Tempo de resolução | Admin audit/reports | MISSING | Não há percentis de resolução | Fixture de lifecycle |
| SO-05 | Funil editorial | Publisher workflow | PARTIAL | Estados existem, sem projeção | Submission→published |
| SO-06 | Desempenho publisher | `reporting.book_engagement` + publisher | PARTIAL | Métricas por obra disponíveis globalmente; autorização por publisher ainda pendente | Autorização por publisher |
| SO-07 | Direitos/licenças | Catalog rights | PARTIAL | Fonte existe, sem painel agregado | Revogação e limitações |
| DQ-01 | Qualidade dos eventos | Behavior events/rejects | PARTIAL | Rejeições existem, attempted/duplicates não consolidados | Fixtures duplicadas/rejeitadas |
| DQ-02 | Outbox | `behavior.outbox` + Micrometer | PARTIAL | Métricas de backlog/idade/tentativas agora exportadas; falta smoke de scrape em produção | Retry, idade e sink |
| DQ-03 | Atribuição | `outcome_attribution` | PARTIAL | Modelo existe, reconciliação incompleta | Orgânico/feed/inválido |
| DQ-04 | Reconciliação | Recommendation + behavior | MISSING | Não há snapshot de mismatches | Ledger vs facts |
| DQ-05 | Freshness | `reporting.projection_health` + Micrometer | PARTIAL | Status, timestamp e record count exportados; falta smoke de scrape em produção | Stale guard e alerta |
| DQ-06 | Schema/telemetria | Event registry | MISSING | Registry/version validation ausentes | Payload inválido/desconhecido |
| DQ-07 | Overhead | Prometheus/Loki/Tempo | MISSING | Stack não existe | Cardinalidade, disco e custo |
| DQ-08 | Compliance/acesso | Admin audit + gateway | PARTIAL | Auditoria parcial, sem reporting export auditado | RBAC e ausência de PII |

## Resultado da auditoria

- `VALIDATED`: 0 painéis.
- `EXISTING`: 0 painéis completos; existem fontes e componentes parciais.
- `PARTIAL`: 42 painéis com dados, métricas ou endpoints reutilizáveis.
- `MISSING`: 25 painéis sem implementação integrada suficiente.
- `BLOCKED`: 0 por dependência externa neste momento.

Os números acima classificam a capacidade do painel, não a existência de uma
tabela ou classe. A implementação deve começar por `dash-02`/`dash-03` para
criar a fundação operacional e por `dash-07`/`dash-08` antes de declarar
conversão ou retenção confiáveis.
