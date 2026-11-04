# Dashboards administrativos — especificação funcional e de produto

> **Status:** SPEC/NOT IMPLEMENTED · **Owner funcional:** Product + Platform · **Issue:** [#8 — Dashboards da seção de administração](https://github.com/jvteodoro/BookRush/issues/8) · **Revisão:** 2026-11-04.
>
> Este documento descreve comportamento desejado, não declara dashboards, datasources, alertas ou instrumentação já implantados. Git é canônico e Backstage publica o TechDocs.

## Objetivo

Dar ao administrador respostas rápidas e auditáveis sobre **saúde da plataforma**, **confiabilidade dos pipelines**, **crescimento e retenção**, **experiência de leitura**, **conversão dos excerpts**, **qualidade do sistema de recomendações**, **moderação e publicação**, sem misturar populações de treino, tráfego real e fixtures.

A rota proposta é **\`/admin/dashboards\`** (na SPA, \`/dashboards\`, respeitando o \`basename\`). Ela será adicionada a \`adminNavigation\` e \`adminRouter\`, independente da rota atual **Overview (\`/\`)**. O Overview continua com cards resumidos e links profundos para painéis específicos; **Users & moderation (\`/operations\`)** continua sendo o workspace de ações administrativas, não um painel de telemetria. Nenhum painel altera estados de domínio.

**Soluções complementares:**
- **Admin Web**: painéis executivos e de produto com filtros semânticos, consultas somente leitura a uma *facade* administrativa de reporting, navegação entre entidades e exportações autorizadas.
- **Grafana OSS**: operações, infraestrutura, serviços, workers, SLO, logs/traces, alertas e exploração avançada; entrada na Admin Web via **links autenticados** e, quando houver UX/SSO testados, painéis incorporados com isolamento de permissões. Não usar iframe anônimo nem credenciais de Grafana expostas no navegador.
- **Prometheus / Loki / Tempo / Alloy** são **propostos** para infraestrutura, não declarados existentes na \`main\`. PostgreSQL fornece projeções agregadas de negócio; Redis/SeaweedFS/Keycloak/Jenkins têm observabilidade por collectors/exporters compatíveis.

Ver [catálogo completo dos painéis](dashboard-panel-catalog.md), [contratos de métricas e dados](dashboard-data-contracts.md), [plano de entrega e testes](dashboard-implementation-plan.md) e [ADR proposto](../../docs/adr/ADR-012-administration-observability-boundaries.md).

## Personas e permissões

| Persona | Necessidade | Acesso proposto |
|---|---|---|
| Platform Operator | diagnóstico, capacidade, disponibilidade, alertas e links logs/traces | dashboards operacionais e Grafana com role restrita |
| Product/Business Analyst | funnel, cohort, retenção, leitura, conteúdo, comparações temporais | **somente agregados** e segmentações autorizadas |
| Recommendation/ML Operator | perfis, estratégias, rollout, diversidade, coverage, experimentos | indicadores agregados e links para evidência/replay autorizados |
| Editorial/Content Operator | pipeline editorial, corpus, direitos, revisão, publicação | dashboards de conteúdo e filas |
| Security/Administrator | qualidade, acessos, auditoria e políticas de retenção | visão global, sem exposição indiscriminada de PII |

As roles/grupos devem ser reconciliados no realm **\`bookrush-platform\`** e autorizados no backend por domínio e recurso; **não** basta ocultar tab no React. O realm de consumidores **\`bookrush\`** não autoriza a interface administrativa. Usuários \`ADMIN_SEED\`/Feed Lab, produção, testes e dados sintéticos devem ter dimensões explícitas; não misturar seus resultados por default.

## Taxonomia de navegação

\`/dashboards\` deve fornecer tabs, com rotas e deep-links estáveis (\`/dashboards?tab=...\`):

1. **Executive** — saúde do negócio e do produto, leituras e conversões, qualidade de dados, comparação com período anterior.
2. **Operations** — disponibilidade, RED/USE, infraestrutura, dependências, logs/traces, deploys, SLOs.
3. **Pipelines & Content** — ingestão, normalização, direitos, SeaweedFS, analytics por fase, coverage e revisão.
4. **Growth & Reading** — acquisition, DAU/WAU/MAU, retenção por cohort, sessões, leitura, biblioteca, streak.
5. **Feed & Recommendations** — funil de viewability à leitura, ranking/estratégias, PFV1 ON/SHADOW/OFF, cold-start, dedup e experimentos.
6. **Social & Publisher** — likes, saves, shares, comentários, denúncias, publicação e desempenho das obras.
7. **Data Quality & Governance** — telemetria, outbox, integridade dos joins, freshness, nomenclatura e privacidade.

A ordem de exibição pode ser refinada visualmente, mas os IDs de métricas \`OP-*, PL-*, GR-*, FD-*, ML-*, SO-*, DQ-*\` do [catálogo](dashboard-panel-catalog.md) são estáveis e testáveis.

## Design da interface

### Cabeçalho e controles globais

Barra superior com título, descrição operacional, status **“dados atualizados em”**, badge de população, acesso a **Definições** e **Abrir no Grafana** (onde aplicável). Controles: período absoluto/relativo (24h, 7d, 30d, 90d, intervalo), granularidade (hora/dia/semana), timezone (default \`America/Sao_Paulo\`, armazenamento UTC), comparação de período anterior, população, plataforma (web/mobile), ambiente, idioma, estratégia de feed, versão/modelo e corpus. Apenas filtros suportados aparecem habilitados; não simular segmentação sem suporte no dado.

**Estado dos filtros** compartilhável em query string com enumerações allow-listed, limites de intervalo e validação no backend. Mudanças cancelam queries obsoletas (React Query), preservam seleção quando muda a tab e apresentam skeleton sem deslocamento de layout. Tabs podem ter filtros específicos (serviço, job, livro, editora, campanha, status).

### Estrutura visual responsiva

- **Desktop:** grid 12 colunas, 3–5 cartões de KPIs acima da dobra, depois séries temporais e decomposições. Limite de densidade: 2–3 gráficos de dimensão média por linha; jamais apresentar todos os painéis simultaneamente.
- **Tablet:** grid de 6 colunas; **mobile:** uma coluna, filtros em drawer, tabelas com colunas selecionáveis ou rolagem horizontal acessível.
- Cards contêm título, unidade, comparação, período, amostra, atualização, definições, *drilldown*. Gráficos possuem legenda, tooltips, export somente autorizado e estado vazio sem “0 falso”.
- Estado **no data** (sem eventos), **not instrumented** (sinal não existe), **stale** (atraso), **restricted** (sem permissão) e **error** (falha técnica) são distintos.
- Cores semânticas e paleta acessível; não depender apenas de cor. Sinais de percentuais exibem denominador e \`n\`; amostras pequenas marcadas “insuficiente”.
- Leituras rápidas: sparklines, time series para tendências, barras para comparação, histograma para distribuições, heatmap para calendário/latência, funnel de coortes, tabela para investigação. Evitar pie charts quando a comparação longitudinal é prioritária.

### Drill-down e navegação

Exemplos de fluxos:
- HTTP 5xx (Operations) → painel serviço específico no Grafana → exemplar trace → logs pelo traceId; os IDs não entram como labels Prometheus.
- Analytics FAILED (Pipelines) → job/item existente no admin \`/analytics/jobs/:jobId/items/:itemId\`; healthcheck HTTP não prova que job terminou.
- CTR baixo (Feed) → estratégia/versão/idioma/faixa de rank → excerpt agregado → página editorial do livro **se houver autorização**; sem exibir texto ou IDs em métricas.
- Falha de direitos → revisão de livro; uso da UI editorial já existente.
- Denúncias em backlog (Social) → \`/operations\` com contexto e permissão preservados.

## Comportamento dos indicadores

1. **Leitura do mesmo fato, uma vez:** \`EXCERPT_IMPRESSION\` válido é impressão efetivamente \`viewable\`, não itens retornados pelo endpoint. \`recommendation.impression\` criado ≠ viewable; aplicar idempotência. Distinguir \`requests\`, \`items served\`, \`viewable impressions\`.
2. **Atribuição:** click/open e leitura somente contam como conversão atribuída se ligados a \`recommendation_item_id\` ou \`impression_id\` válidos, mesmo sujeito/livro, dentro de janela; ações orgânicas ficam em categoria separada. Não fazer join apenas por livro.
3. **Dwell:** informar tempo visível real e suprimir outliers/background; a instrumentação atual pode ter \`EXCERPT_DWELL\` e \`DWELL\` e exige normalização versionada antes de exibir taxa ou média.
4. **Leitura:** evento de progresso é um sinal, **não** equivale a leitura concluída; \`BOOK_COMPLETE\` e marco de progresso têm definição distinta.
5. **DAU / retenção:** usuários autenticados únicos em ações significativas, separados de bots, administradores e fixtures; aquisição e sessão ativa são conceitos diferentes.
6. **Score de modelos:** scores heurísticos/semânticos **não** são probabilidades calibradas. Engajamento observado e cobertura são os indicadores de resultado.
7. **Relação feed/conversão:** CTR por versão não é efeito causal. Mudanças de modelo exigem coortes comparáveis e, para causalidade, experimentação com assignment persistido.
8. **Valores ausentes:** não preencher null com zero nem dividir por 0. Mostrar \`—\` + explicação.
9. **Relação entre observações:** análises por tentativa/lease não contam tentativas históricas órfãs como falha atual de job.
10. **Numerador/denominador:** toda taxa tem definição, janela e universo documentados; filtros se aplicam simetricamente.

## Frequência, experiência e SLO de consulta (propostos)

| Classe | Atualização alvo | Backend/datasource | Consulta UI |
|---|---|---|---|
| Infra/RED | scrape 15–30 s (ajustável), avaliação alerta 1 min | Prometheus/Grafana | refresh 30–60 s sem carga excessiva |
| Logs/traces | atraso-alvo até 60 s | Loki/Tempo | deep-link Grafana, não polling pesado na SPA |
| Workers/pipelines | 1–5 min; jobs individuais por API existente | Prometheus + snapshots SQL | refresh 60 s (detalhe sob demanda) |
| Business operacional | refresh agregados 5–15 min | projeções PostgreSQL | 5–15 min |
| Cohorts, retenção, experimentos | cálculo diário + backfill | projeções SQL versionadas | consulta sob demanda |

São **metas iniciais**, não SLA/SLO medidos; validar consumo de disco/carga/volume e taxa de eventos antes de aprová-las.

## Integração técnica recomendada

\`\`\`text
Browser (Admin Web, OIDC PKCE/realm platform)
     |
     +--> /api/admin/v1/reporting/*  [novos endpoints futuros]
     |       admin-reporting facade (read-only / autenticação forte / cache / rate limit)
     |             +--> snapshots/projeções reporting (PostgreSQL)
     |             +--> contratos públicos internos de owners, quando necessário
     |
     +--> links Grafana SSO, sem bearer/segredos na URL
                 Grafana datasources: Prometheus, Loki, Tempo, reporting somente leitura

Serviços Java --Micrometer---> Prometheus (coleta privada)
Java/Python/Web --OTel/Alloy--> Loki/Tempo
infra exporters ----------------> Prometheus
behavior / recommendation / reader / catalog / analytics --> projeções do reporting
\`\`\`

**Fronteiras inegociáveis:** a SPA não consulta PostgreSQL, Prometheus privado nem tabelas transacionais de outros bounded contexts; manter schemas de ownership, evitar cross-service JDBC em runtime, não abrir \`/actuator/prometheus\` publicamente, restringir exportadores a rede privada. Para jobs/itens use APIs administrativas existentes. Para dados históricos de múltiplos owners, usar exportações/snapshots de leitura com corte consistente e versionamento documentado.

## Relação com código atual

- Admin atual: \`admin-web/src/app/Dashboard.tsx\` só mostra três cards (\`Imported books\`, \`Analytics runs\`, \`Training mode Ready\`) e quick links; \`adminNavigation\` e \`adminRouter\` ainda não possuem \`/dashboards\`.
- API e pipeline: \`services/behavior-service\` contém \`behavior.events\`, \`daily_book\`, \`user_book_signal\`, \`outcome_attribution\`, outbox. \`recommendation-service\` contém request/item/impression ledger + PFV1 e \`FeedMetrics\`. \`reader-state-service\` persiste sessões, progresso e streak. \`book-analytics-service\` registra job/item e duração de fases.
- [PFV1 operations](../../docs/recommendation/personalized-feed-v1-operations.md): caminhos \`ON/OFF/SHADOW\`, confidence, fallback, overlap, rollout; labels devem ser interpretados como *counters de execução*, não um resultado causal.
- Métricas já presentes no Java **não** garantem Prometheus disponível em todos os serviços; instrumentação/exportação e acessos serão verificados por serviço no aceite.

## Não objetivos do primeiro release

Não criar data warehouse externo, Kafka, ML autorregulado, dados financeiros inventados ou export de PII, nem chamar “A/B test” uma comparação observacional. Não implementar ações de administração dentro dos painéis. Não depender de Grafana para a autenticação do produto. Não habilitar scraping nem logs de URLs S3 assinadas, payloads integrais, tokens, livros e vetores.

## Materiais relacionados

- [Catálogo de métricas e visualizações](dashboard-panel-catalog.md)
- [Contratos de dados e atribuição](dashboard-data-contracts.md)
- [Plano de implementação e testes](dashboard-implementation-plan.md)
- [Documentação de operação do admin](operations.md)
- [Owner dos bounded contexts](../../docs/adr/ADR-0034-product-bounded-contexts.md)
- [Política docs-as-code](../../docs/documentation-policy.md)
