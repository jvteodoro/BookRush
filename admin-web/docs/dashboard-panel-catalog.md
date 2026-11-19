# Catálogo de 36 painéis — Dashboard Redesign V2

IDs são referências documentais e não aparecem nos títulos da interface. A ausência de fonte permanece explícita; nenhum valor é estimado.

## Business Overview

| ID | Painel | Visualização | Definição / unidade | Fonte | Estado |
|---|---|---|---|---|---|
| EX-01 | Leitores engajados | KPI + sparkline | Usuários únicos com READ_SESSION ≥60 active seconds; regra significant-read-v1 | `behavior.events / reporting.reader_day` | Disponível para evento corretamente identificado |
| EX-02 | Engajamento | Cards | Minutos ativos, minutos por leitor, sessões e livros iniciados | `reporting.reader_day` | Parcial |
| EX-03 | Funil do produto | Funnel | Impressões únicas viewable e attribution válida até conclusão; outcomes maduros em sete dias | `reporting.feed_daily` | Parcial; funil por usuário/coorte comparável indisponível |
| EX-04 | Evolução da atividade | Linhas | DAU, WAU, MAU em unidades separadas | `reporting.reader_day` | Disponível |
| EX-05 | Retenção | Heatmap de coortes | Retenção D1, D7, D30 de leitores significativos | `reporting.retention_cohort` | Heatmap; cobertura de D30 e validação no banco pendentes |
| EX-06 | Performance do catálogo | Barras horizontais | Top 10 livros por leitores engajados e tempo ativo | `reporting.book_engagement` | Parcial; exigir cohort comparável |

## Operations

| ID | Painel | Visualização | Definição / unidade | Fonte | Estado |
|---|---|---|---|---|---|
| OP-01 | Saúde da plataforma | Cards | Serviços scrape, incidentes, 5xx e P95 feed | `Prometheus` | Grafana provisionado; validação de datasource/runtime pendente |
| OP-02 | Jornadas críticas | Matriz de status | Login, feed, abertura, progresso e ingestão de eventos | `Smokes existentes / synthetic metrics` | Não instrumentado; smokes periódicos isolados pendentes |
| OP-03 | Saúde dos serviços | Tabela por severidade | Disponibilidade, P95, erros, throughput e saturação | `Prometheus` | Técnico disponível; resumo Grafana em revisão |
| OP-04 | Latência das jornadas | Séries temporais | P95 por jornada e SLO de referência | `Prometheus synthetic metrics` | Indisponível |
| OP-05 | Recursos e capacidade | Barras | CPU/RAM host, disco, PostgreSQL e pools JDBC | `Node exporter, cAdvisor, PostgreSQL, JVM` | Parcial |
| OP-06 | Incidentes | Tabela priorizada | Problema, serviço, duração, impacto, severidade e investigação | `Prometheus alerts / logs` | Alertas existentes; impacto funcional indisponível |

## Recommendation Intelligence

| ID | Painel | Visualização | Definição / unidade | Fonte | Estado |
|---|---|---|---|---|---|
| FD-01 | Eficiência do feed | Cards | CTR viewable, conversão em leitura, dwell e repetição | `feed_daily / outcomes` | Cards calculados sobre eventos; dwell mediano não existe na fonte |
| FD-02 | Estratégias de recomendação | Barras agrupadas | CTR e conversão por estratégia/modelo com amostra | `recommendation_daily / quality` | Observacional; não causal |
| FD-03 | Caminhos PFV1 | Área empilhada | Percentuais personalized, fallback e cold-start | `PFV1 path counters` | Disponível via Prometheus |
| FD-04 | Motivos de fallback | Pareto | Quantidade e proporção por motivo | `PFV1 reason counters` | Disponível via Prometheus |
| FD-05 | Performance por posição | Linhas | Viewability, CTR e leitura por rank bucket | `recommendation_position_funnel_daily` | Parcial; leituras atribuídas por rank pendentes |
| FD-06 | Funil de candidatos | Funnel | Retrieved, scored, selected, served, viewable | `recommendation candidate funnel` | Disponível; denominadores únicos a validar |
| FD-07 | Diversidade | Linhas e barras | Repetição, livros únicos e concentração de impressões | `recommendation_daily` | Parcial |
| FD-08 | Desempenho dos excerpts | Scatter plot | Score analítico verificado versus CTR e leitura | `excerpt features + outcomes` | Indisponível sem vínculo versionado confiável |

## Content Intelligence

| ID | Painel | Visualização | Definição / unidade | Fonte | Estado |
|---|---|---|---|---|---|
| CT-01 | Disponibilidade do corpus | Cards | Ativo, reader-ready, analisado e feed-ready | `content_supply / excerpt_production` | Parcial; alinhar regra exata do recomendador |
| CT-02 | Funil de conteúdo | Funnel | Conjuntos aninhados de livros únicos até elegível/rankeado | `catalog, rights, assets, analytics` | Parcial; conjunto aninhado não consolidado |
| CT-03 | Bloqueios de disponibilidade | Pareto | Livros bloqueados por motivo real | `rights / content jobs` | Parcial; drill-down individual disponível em páginas existentes |
| CT-04 | Composição do catálogo | Barras empilhadas | Disponibilidade por idioma e gênero | `catalog metadata` | Parcial; gênero não classificado aqui |
| CT-05 | Cobertura das recomendações | Barras | Elegíveis recomendados versus nunca recomendados | `content coverage` | Disponível |
| CT-06 | Oportunidades editoriais | Tabela priorizada | Muito exibidos/pouco abertos; conversão alta/baixa exposição | `feed impressions + outcomes` | Parcial; thresholds sem baseline definidos |

## Analytics Factory

| ID | Painel | Visualização | Definição / unidade | Fonte | Estado |
|---|---|---|---|---|---|
| PL-01 | Estado da produção | Cards | Pendentes, executando, concluídos e falhas terminais | `pipeline / ingestion snapshots` | Disponível |
| PL-02 | Capacidade | Cards | Livros/hora e capacidade diária por itens únicos concluídos; previsão backlog | `analytics job history` | Throughput/ETA não expostos no Admin; previsão indisponível |
| PL-03 | Evolução das filas | Série temporal | Pendentes, entradas e conclusões ao longo do tempo | `ingestion queue snapshots` | Disponível |
| PL-04 | Gargalos | Barras horizontais | Mediana e P95 por fase | `analytics phase histogram` | Disponível onde instrumentado |
| PL-05 | Falhas | Pareto | Códigos de erro e itens afetados | `analytics jobs` | Parcial; taxonomy de códigos |
| PL-06 | Jobs que exigem atenção | Tabela | Heartbeat, lease, progresso, retries e idade da fila | `analytics job service` | Parcial; links existentes para detalhes |

## Data Quality

| ID | Painel | Visualização | Definição / unidade | Fonte | Estado |
|---|---|---|---|---|---|
| DQ-01 | Confiabilidade | Cards | Projeções saudáveis/atrasadas/com erro e UNKNOWN | `projection_health + population` | Disponível após freshness separado |
| DQ-02 | Integridade dos eventos | Barras empilhadas | Aceitos, rejeitados e duplicados identificáveis | `behavior events / rejects / outbox` | Parcial; duplicados identificáveis limitados |
| DQ-03 | Qualidade da attribution | Funnel | Outcomes válidos, atribuídos, orgânicos, inválidos e órfãos | `outcome_attribution` | Disponível |
| DQ-04 | Saúde das projeções | Tabela por impacto | Refresh, watermark, lag, estado e erro | `projection_health` | Disponível |
