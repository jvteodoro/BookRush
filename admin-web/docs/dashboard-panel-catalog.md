# Catálogo de painéis e visualizações — issue #8

> **Status:** especificação · **Versão:** `dashboard-catalog-v1` · IDs imutáveis; P0 = fundação essencial; P1 = release funcional completo; P2 = expansão dependente de dados/experimentos. A coluna “fonte” é a fonte **esperada**, não confirmação de implementação. Todos os resultados são dados reais, nunca mock em produção.

A composição de cada painel deve conter: ID, título, pergunta respondida, tipo de visualização, unidade, numerador/denominador se taxa, janela, população, fonte de verdade, estado de instrumentação, filtros habilitados, tooltip com fórmula, volume/n e drill-down. **Os gráficos abaixo não devem ser renderizados como zeros quando a fonte não existe.**

## Executive

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `EX-01` | Visão geral de atividade | Stat + sparkline | usuários ativos, sessões, minutos de leitura | `reporting.daily_activity` | P1 |
| `EX-02` | Funil principal | Funnel | viewable→open→meaningful read→complete (mesma coorte) | `reporting.feed_conversion` | P1 |
| `EX-03` | Saúde do produto | Status tiles | SLO, feed funcional, freshness, eventos rejeitados | `Prometheus + reporting snapshots` | P1 |
| `EX-04` | Evolução de engajamento | Time series | DAU + horas ativas + leitura significativa | `reporting.daily_activity` | P1 |
| `EX-05` | Conteúdo disponível | Funnel + grouped time series | livros ativos → reader-ready por disponibilidade/direitos → excerpt-ready; counts aninhados, não aditivos | `reporting.content_supply_snapshot` | P1 |
| `EX-06` | Comparação período anterior | Delta cards | variação absoluta/relativa por KPI com denominador | `reporting.daily_activity` | P1 |

## Operations

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `OP-01` | Mapa de disponibilidade | Stat grid | up/down por job/serviço/instance; freshness é mostrada separadamente no Admin Web | `Prometheus up + projection health` | P0 |
| `OP-02` | Taxa de requisições | Time series | req/s por serviço e URI template (`bookrush-java`) | `http_server_requests_seconds_count{service,uri}` | P0 |
| `OP-03` | Erros HTTP | Time series | 4xx/5xx req/s por serviço, URI template e status; proporção sem tráfego exibida como ausência; alerta agregado exige >0.1 req/s e razão inicial 1%, sem SLO aprovado | `Micrometer + Prometheus` | P0 |
| `OP-04` | Latência por serviço/rota | Time series | p50/p95/p99 por serviço e URI template, usando buckets SLO 0.1–5s; target Java isolado do Keycloak | `Micrometer + Prometheus bookrush-java` | P0 |
| `OP-05` | JVM e containers | Time series | heap, process/container CPU, working-set, OOM, GC e threads; restarts ainda não são exibidos | `JVM + cAdvisor` | P0 |
| `OP-06` | PostgreSQL | Time series | connections, deadlocks, buffer cache hit ratio e commits/rollbacks do banco `bookrush`; slow queries/WAL/bloat não estão incluídos | `postgres_exporter + pg_stat_database` | P0 |
| `OP-07` | Redis | Time series | hit ratio, memoria, evictions, erros e latência | `redis_exporter` | P1 |
| `OP-08` | SeaweedFS/S3 | Time series | throughput, latência média e erros por operação (`operation/result`); capacidade do volume ainda sem métrica | `catalog-service bookrush.storage` | P1 |
| `OP-09` | Gateway/Keycloak | Time series | status/5xx e p95 do gateway; outcomes/status HTTP e p95 do Keycloak; não representa eventos individuais de autenticação | `Traefik + Keycloak HTTP metrics` | P1 |
| `OP-10` | Jenkins e releases | Event timeline | status build/deploy, versão/digest, duração, rollback | `reporting.release_event` via Jenkins S2S | P1 |
| `OP-11` | Dependency tracing | Trace drilldown | BFF→recommendation→analytics/behavior + PostgreSQL | `Tempo + OTel` | P1 |
| `OP-12` | Incidentes/SLO | Burn-rate + table | SLI disponibilidade e latência, incidentes, alertas/runs | `Prometheus recording/alert rules` | P1 |

## Pipelines & Content

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `PL-01` | Ingestão | Time series + status chart/table | discovered, acquired, normalized, processed, attempt_failed, retry e failed por segundo; estados diários dos itens | `bookrush_ingestion_items_total{result}` + `reporting.ingestion_daily` | P0 |
| `PL-02` | Fila de ingestão | Stacked area | PENDING/RETRY_WAIT/RUNNING/FAILED/DONE/CANCELLED, idade do task pendente mais antigo, leases expirados | `reporting.ingestion_queue_snapshot` ← `catalog.ingestion_task`; gauges `bookrush_ingestion_queue_*` | P0 |
| `PL-03` | Pipeline de analytics | Stacked area | jobs e items por estado; throughput | `analytics job/item` | P0 |
| `PL-04` | Tempo por fase | Heatmap + p95 | ITEM_TOTAL, embeddings, selection, persistence por tentativa | `analysis_phase_timing + Prometheus` | P0 |
| `PL-05` | Erros de analytics | Bars + table | error_code por fase e tentativa; jobs com stuck leases | `analysis_job_item/phase log` | P0 |
| `PL-06` | Infra ML | Time series | FastText/spaCy/BGE/NLI runtime, memory, tempo/model unavailable | `OTel runtime / model status` | P1 |
| `PL-07` | Produção de excerpts | Barras + tabela | gerados por outcome/reason, idioma, direitos e método; sem inferir aprovação/publicação inexistentes | `reporting.excerpt_production_snapshot` | P1 |
| `PL-08` | Cobertura de corpus | Tabelas e barras | idioma, direitos, readiness e distribuição dos decis por ranker/configuração | `reporting.content_coverage_snapshot`, `reporting.content_rank_score_snapshot` | P1 |
| `PL-09` | Qualidade editorial | Bars + table | aprovação/rejeição, motivo, fila, tempo de review | `catalog reviews + analytics annotations` | P1 |
| `PL-10` | Qualidade de importação | Barras + tabela | tentativas/retries, bucket de erro, direitos atuais e estado de asset por origem | `reporting.import_quality_daily` | P1 |
| `PL-11` | Frescor e backlog | Tabela operacional | filas pendentes/idade/falha/stuck e freshness/watermark de projeções | `reporting.backlog_snapshot` | P1 |

## Growth & Reading

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `GR-01` | Usuários únicos | Time series | DAU/WAU/MAU por plataforma; identidade distinta por issuer + subject, não contagem de contas | `behavior.events → reporting.reader_day` | P1 |
| `GR-02` | Novos e recorrentes | Stacked time series | first seen e returning; population separada | `reporting.reader_activity` | P1 |
| `GR-03` | Retenção cohorts | Retention heatmap | D1/D7/D30 por coorte de TechnicalIdentity (issuer + subject), só em coortes maduras | `reporting.retention_cohort` | P1 |
| `GR-04` | Engajamento de leitores | Time series | sessões, minutos ativos, ativos por usuário | `reader_state session + events` | P1 |
| `GR-05` | Distribuição de leitura | Stacked bar + tabela | sessões por duração (<1m, 1–5m, 5–15m, 15m+), P50/P90 e leitores distintos por issuer + subject | `reporting.reading_daily` | P1 |
| `GR-06` | Progresso | Funnel + cohort table | pares leitor/obra agrupados pelo primeiro início de sessão/progresso e marcos únicos 10/50/100 já alcançados | `reporting.reading_progress_daily` ← `reader_state.session + behavior.events` | P1 |
| `GR-07` | Biblioteca | Current snapshot + action table | pares salvos, obras distintas, subject keys distintos; save/unsave ao longo do tempo | `reporting.library_state_snapshot + behavior events` | P1 |
| `GR-08` | Streaks e hábitos | Histogram + calendar | streaks, retorno, dias ativos | `reader_state reading_day` | P2 |
| `GR-09` | Web x Mobile | Grouped bars | atividade e conversão por plataforma, apenas se atribuição suportada | `client telemetry versioned` | P2 |
| `GR-10` | Top books e idioma | Bar chart + table | minutos, leitores, conclusão por livro/idioma; supressão n pequeno | `reporting.book_engagement` | P1 |

## Feed & Recommendations

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `FD-01` | Funnel de recomendação | Funnel | served→viewable→opened→meaningful→complete; coorte e janela | `recommendation ledger + outcome` | P1 |
| `FD-02` | CTR viewable→open | Time series | aberturas atribuídas em coortes maduras de 7 dias / impressões viewable maduras | `reporting.feed_daily` | P1 |
| `FD-03` | Dwell de excerpt | Histogram + time series | tempo de exposição, skip, thresholds | `normalized telemetry` | P1 |
| `FD-04` | Taxa de abertura/leitura | Time series | conversão read_10/read_50/complete por coorte | `reporting.feed_attribution` | P1 |
| `FD-05` | Comparação de estratégia | Grouped bars | PERSONALIZED, fallback, cold-start, control; sem causalidade | `request strategy + outcomes` | P1 |
| `FD-06` | PFV1 caminhos | Time series | contadores de FALLBACK/SHADOW/PERSONALIZED e motivos; ON pode degradar para FALLBACK | `FeedMetrics + Grafana bookrush-feed` | P0 |
| `FD-07` | Performance do ranking | Time series | p50/p95/p99 por etapa, cache hit, SQL, timeouts | `FeedMetrics + analytics metrics` | P0 |
| `FD-08` | Coverage e diversidade | Time series + bars | itens servidos, livros viewable distintos e repetição por leitor/livro em 30d; candidato→scored→selected no pool limitado, sem denominador do universo elegível do catálogo | `reporting.recommendation_daily + FeedMetrics` | P1 |
| `FD-09` | Relevância por posição | Heatmap | impressões, viewability e CTR em coorte madura por rank bucket (não user ID) | `reporting.recommendation_position_funnel_daily` + outcomes por impressão | P1 |
| `FD-10` | Experimentos | Table + intervals | assignment/control/treatment, guardrails, IC/efeito quando validado | `new experiment assignment` | P2 |
| `FD-11` | Shadow overlap | Time series | overlap/rank movement comparados e amostra | `PFV1 shadow metrics` | P0 |
| `FD-12` | Qualidade do perfil | Histogram + table | buckets de tamanho/confiança do perfil, cobertura de fadiga, cold-start e requests agregados | `reporting.recommendation_profile_quality_daily + FeedMetrics` | P1 |
| `FD-13` | Livro/excerpt | Table + bars | CTR e leitura por excerpt/version + eligibility, limite de acesso | `reporting.excerpt_performance` | P2 |

## Social & Publisher

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `SO-01` | Interações sociais | Time series + table | eventos aceitos de like/unlike, save/unsave, share e comment, com atores/obras distintos; separados dos contadores de atividade social | `reporting.social_daily` ← `behavior.events` e `social.activity` | P1 |
| `SO-02` | Engajamento de obras | Table + bars | ratio interações/viewable, leitores, conclusão (sem double count) | `reporting.book_engagement` | P1 |
| `SO-03` | Moderação | Stacked bars + age histogram | reports open/in_review/resolved/dismissed; unresolved cases aged <1, 1–3, 3–7, 7+ days by creation cohort; approved SLA when defined | `reporting.moderation_daily` ← `social.reports` | P1 |
| `SO-04` | Tempo de resolução | Time series + percentiles | tempo p50/p95, backlog; reopen count | `admin report audit` | P1 |
| `SO-05` | Funil editorial/publisher | Funnel + cohort table | submissões distintas que alcançaram revisão→aprovação→publicação→ativa, agrupadas pela data da submissão | `reporting.editorial_submission_funnel_daily` ← `publisher.submission/review/published_work` | P1 |
| `SO-06` | Desempenho publisher | Table + trend | impressões, aberturas, leituras e crescimento por obra autorizada; células e períodos abaixo de 5 leitores únicos são suprimidos | `publisher + behavior S2S aggregates` | P1 |
| `SO-07` | Direitos/licenças | Stacked bars + table | decisões atuais por tipo, território e estado temporal, com atestações de publisher em série separada | `reporting.rights_status_daily` ← `catalog.rights_decision + publisher.rights_attestation` | P1 |

## Data Quality & Governance

| ID | Gráfico / pergunta | Visual | Métricas / dimensões essenciais | Fonte proposta | Etapa |
|---|---|---|---|---|---|
| `DQ-01` | Qualidade dos eventos | Time series | aceitos, rejects, duplicates, taxa por motivo | `behavior events/rejects/ingestion` | P0 |
| `DQ-02` | Outbox | Time series + stat | pending, attempts, max age, lag until published, sink errors | `behavior.outbox + analytics sink` | P0 |
| `DQ-03` | Atribuição | Stacked bars | eventos correlacionados vs orgânicos vs inválidos por outcome | `behavior.outcome_attribution` | P1 |
| `DQ-04` | Reconciliação | Table | impression ledger vs viewable facts; mismatches, duplicidade | `recommendation + behavior snapshot` | P1 |
| `DQ-05` | Freshness | Stat + heatmap | updated_at, watermark, atraso por projeção, stale guard | `reporting metadata` | P0 |
| `DQ-06` | Schema/telemetria | Table + bars | schema version, desconhecidos, client version, payload invalid | `event registry/telemetry` | P1 |
| `DQ-07` | Observabilidade overhead | Time series | scrape failures, series count, ingestion, disk, retention | `Prometheus/Loki/Tempo` | P1 |
| `DQ-08` | Compliance e acesso | Table | auditoria de acesso a reporting e export; export operador-only, allowlisted e sem conteúdo nos logs | `admin audit + gateway` | P1 |

## Regras de visualização por tipo

- **Séries temporais:** eixo temporal real UTC convertido para timezone de exibição, pontos faltantes transparentes, granularidade de acordo com janela e limites de query, delta de período anterior somente com duração equivalente; counters com \`rate\`/\`increase\` e reset tratado.
- **Funnel:** contagem de unidades únicas do **mesmo cohort elegível** (impressões/itens/pares leitor-livro), com estágio de entrada, janela e regras de atribuição. Jamais dividir eventos de universo diferente; pode haver outcomes orgânicos separados.
- **Percentis:** histogramas agregáveis, nunca média de percentis de instâncias. Exigir buckets/função e amostra suficientes; usar \`histogram_quantile\` com série \`_bucket\`.
- **Retenção:** cohort de primeira atividade significativa no fuso de negócio; Dn no dia n após a primeira atividade, inclusive intervalos sem login; excluir cohorts não maduros, indicar \`n\`. Não substituir por \`streak\`.
- **Histograma/heatmap:** valores reais observados, escala e número de amostras; incluir percentis e limites de buckets.
- **Tabelas:** sort/paginação server-side, limites por tamanho, busca restrita, máximo top-N 50 por default, total e timestamp; páginas por livro/editora só se acesso autorizado e consultas read-only.
- **Ranking:** rank = posição persistida da decisão, não ordenação de UI; múltiplos modelos/versões no mesmo painel apenas quando cohorts e elegibilidade comparáveis.
- **Empty/error:** distingir sem dados, sem instrumentação, não elegível, sem permissão, atraso e falha; auto-refresh suspenso na aba em background.
- **Acessibilidade:** title/aria text, dados tabulares alternativos, legenda, contraste e teclado; avisos explícitos de dados parciais.

## Filtros e dimensões

Globais: \`from\`, \`to\`, \`timezone\`, \`granularity\`, \`comparePrevious\`, \`environment\`, \`population\`; selecionáveis conforme panel: \`platform\`, \`country\` **somente se coletado legalmente e validado**, \`language\`, \`bookId\` (**somente reporting seguro**), \`publisherId\`, \`feedPath\`, \`strategy\`, \`modelVersion\`, \`experimentId\`, \`rankBucket\`, \`service\`, \`jobStatus\`, \`jobId\`, \`phase\`, \`errorCode\`. Um filtro não suportado desabilita painéis afetados com explicação, não ignora silenciosamente filtros.

## Indicadores prioritários de integridade

\`DQ-01\` e \`DQ-04\` são dependências de \`EX-02\`/\`FD-01\`/\`FD-02\`. Se deduplicação, identificação do sujeito ou vínculo ao item de ranking não passarem os testes, o funil exibe **DADO NÃO CONFIÁVEL** e não recomenda decisões com base nesse indicador.

## Checklist de revisão de cada painel

- [ ] Definição unívoca e fonte testada com fixture.
- [ ] Permissões backend e filtros coerentes.
- [ ] “No data”, “not instrumented”, “stale” e erro exercitados.
- [ ] Datas, fusos, cardinalidade e contagens reconciliadas.
- [ ] Drill-down funcional e link de documentação/owner.
- [ ] Renderização desktop/tablet/mobile, teclado, export seguro.
- [ ] Data de última atualização, período e \`n\` exibidos.
