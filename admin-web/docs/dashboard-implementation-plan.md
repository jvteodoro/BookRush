# Plano de implementação — dashboards da administração (#8)

> **Escopo:** especificação para agente/implementadores, **sem** afirmar código, dashboards provisionados ou gates de produção já entregues. Dependências e checks em ordem. A issue #8 é épico de entrega; dividir em Beads e PRs incrementais, com owner, evidências e bloqueios.

## Estratégia e dependências

\`\`\`text
A. ADR + inventário + contrato de KPI + catálogo UX
    |
    +--> B. Instrumentação e stack observability
    |       +--> C. Dashboards de operações + alertas + SSO
    |
    +--> D. Qualidade de eventos/attribution + exports seguros
            +--> E. Projeções reporting + API RBAC
                    +--> F. Admin Web dashboards + business panels
                            +--> G. Retenção, cohorts, ML experiments
    \`--> H. Integração, testes de segurança e evidência produção
\`\`\`

A fase A corresponde a este pacote documental, não à entrega técnica. Implementar DQ e attribution **antes** de reportar CTR/conversão como confiáveis.

## Matriz de trabalho para Beads

| ID sugerido | Prioridade | Implementação concreta | Depende de | Evidência requerida |
|---|---|---|---|---|
| \`dash-01\` | P0 | Aprovar ADR, catálogo 67 painéis, owner e definições; auditar fontes atuais, matriz de métricas existentes x faltantes | — | matriz com versão do código, testes, revisão |
| \`dash-02\` | P0 | Adicionar \`infrastructure/observability/\` e Compose isolado/profile: Grafana, Prometheus, Alloy, Loki, Tempo; redes privadas, volumes, health, provisionamento, limites/retention, exporters básicos | dash-01 | \`compose config\`, start/stop/restore, targets UP e armazenamento |
| \`dash-03\` | P0 | Prometheus/Micrometer uniformes: BOM/starter, Actuator, histogram SLO, HTTP/JVM/Hikari; varredura de segurança dos endpoints | dash-02 | scrape verificado em cada serviço + teste 401 no gateway |
| \`dash-04\` | P1 | Tracing OpenTelemetry e logs JSON, W3C propagation em BFF→recommendation→analytics/behavior, Python runtime e async; Tempo/Loki/exemplars | dash-02/03 | trace de feed e log correlacionado sem token |
| \`dash-05\` | P0 | Dashboards Grafana OP-01..OP-06, PL-01..PL-05, FD-06/07/11, DQ-01/02/05 com provisioning e owners | dash-03 | JSON versionado, screenshots, queries vivas, sem mock |
| \`dash-06\` | P0 | SSO Grafana Keycloak platform e RBAC + regras de alerta SLO/queue/freshness, rotas/restrições de acesso | dash-02/05 | prova operator vs analyst, falha disparada, runbook |
| \`dash-07\` | P0 | Auditar/normalizar \`BOOK_OPEN\`, \`EXCERPT_IMPRESSION\`, \`READ_SESSION\`, dwell, social, dedup e idempotência; versionar população e origem | dash-01 | fixtures web/mobile, reconciliação e relatório de migração |
| \`dash-08\` | P0 | Garantir correlation item/impression (PFV1) em actions server/client, reconstrução outcome e sink sem PII; janela de 7d versionada | dash-07 | testes multiusuário/multilivro/multiple views, joins exatos |
| \`dash-09\` | P1 | Criar ingest/export read-only e schema \`reporting\` com fatos/projeções, snapshots, refresh, lineage/checksum, role SQL read-only, índices e limites | dash-08 | snapshots idempotentes, backfill, freshness e explain |
| \`dash-10\` | P1 | Facade reporting (pode ser módulo no admin-service ou serviço novo se ownership/escala justificar), OIDC scopes, SQL read-only, OpenAPI, cache, rate limit, pagination, authZ por filtro | dash-09 | contratos e testes de segregação/negação |
| \`dash-11\` | P1 | Criar \`/dashboards\` React Router + navigation, tabs, filtros, query state, React Query, skeleton, states, responsividade, gráficos + tabelas, deep links | dash-10 | e2e desktop/mobile, accessibility, selectors |
| \`dash-12\` | P1 | Implementar painéis EX/GR/FD/SO/PL conforme catálogo; alinhar versões, unidades, confiança, amostra, atribuição, definição e export | dash-11 | snapshots SQL concordantes com fixtures e displays |
| \`dash-13\` | P2 | Retention/cohort snapshots, demografia consentida se existir, experiment assignment/guardrails e comparação controlada de variantes | dash-08/09/12 | testes de coortes maduras e experimento real |
| \`dash-14\` | P1 | Fluxos incidentes, cross-links Grafana, runbooks, Backstage/TechDocs e Jenkins job de deploy/provisionamento | dash-05/06/11 | smoke navegacional e documentação publicada |
| \`dash-15\` | P0 | Gate final: Compose/CI, segurança, carga, controle de cardinalidade, retenção, recovery, dashboards sem dados artificiais; piloto com população real e auditoria | dash-05..14 (P2 pode ficar deferido) | relatório GO/NO-GO com hash de código/imagem e lacunas |

Cada bead exige critérios objetivos e arquivos alterados, depende explicitamente do anterior quando preciso; **não** marcar complete apenas por scaffold, testes isolados ou dashboards vazios.

## Arquivos e responsabilidades propostos

\`\`\`text
infrastructure/observability/
  compose.yaml
  grafana/provisioning/{datasources,dashboards,alerting}/
  grafana/dashboards/{operations,pipelines,product,recommendation,data-quality}/
  prometheus/{prometheus.yml,alerts.yml,recording-rules.yml}
  alloy/config.alloy
  loki/config.yaml
  tempo/config.yaml
  exporters/
  README.md
platform/bookrush-observability-spring-boot-starter/
  ... (metric convention, OTEL and JSON log integration)
admin-web/src/features/dashboards/
  DashboardHub.tsx
  components/{MetricCard,TimeSeries,Funnel,Distribution,FilterBar,DataState}.tsx
  queries/{reportingClient,types}.ts
admin-web/src/app/{adminRouter.tsx,navigation.ts}
services/admin-service/ (facade reporting opcional)
services/<domain-owner>/ (export ou instrumentation)
docs/adr/ADR-012-administration-observability-boundaries.md
admin-web/docs/dashboard-*.md
\`\`\`

Exemplos de caminhos são sugestão, preservar convenções já existentes; **nunca** modificar migrations aplicadas: novas mudanças recebem Flyway V+ subsequentes.

## Milestone MVP e Definition of Done

### MVP operacional P0

- [ ] \`docker compose\` sobe stack e todos os exporters previstos com recursos limitados, sem abrir métricas nem dados analíticos no gateway público.
- [ ] Grafana autenticado no Keycloak plataforma com grupos e roles de privilégio mínimo; sem admin anônimo nem tokens no frontend.
- [ ] Cada aplicação alvo possui \`/actuator/prometheus\` ou equivalente protegido via rede interna, com \`up=1\`, JVM e HTTP requests; dashboards trazem \`service.name\`, ambiente e versão.
- [ ] OP-01..06, PL-01..05, FD-06/07/11 e DQ-01/02/05 funcionam com dados mensurados; partes sem fonte são \`NOT_INSTRUMENTED\`.
- [ ] Há alerta demonstrável para indisponibilidade, taxa de 5xx, workers stuck, outbox lag e falha no freshness.
- [ ] A instrumentação não registra token, subject, conteúdo de livros, URL assinada nem IDs de usuário em tags.

### MVP negócio P1 (a completar após P0)

- [ ] \`/dashboards\` está na navegação, com 7 abas especificadas, filtros, período, comparação, deep links, tabelas e estados acessíveis.
- [ ] \`EX-01..06\`, \`GR-01..07/10\`, \`FD-01..09/12\`, \`SO-01..07\`, \`PL-06..11\`, \`DQ-03/04/06..08\` exibem dados reais quando instrumentados, com contrato \`dashboard-response-v1\`; P2 ficam identificados.
- [ ] Todas as taxas de conversão/funnel usam o mesmo cohort + attribution, registram denominador, \`n\` e watermark.
- [ ] O frontend nunca tem conexão direta ao banco nem passa SQL; facade RBAC restringe por população, domínio e intervalo.
- [ ] Séries vazias, backend unavailable, 401/403, stale, low sample e fixtures não são confundidos com zero.
- [ ] Exportações acessíveis somente para papéis permitidos, logs e auditoria próprios, com query limitada.

## Gates de teste especificados

| Gate | Ensaio | Resultado de aprovação |
|---|---|---|
| Dados e fórmulas | fixtures de 2 usuários, 2 livros, 3 impressões, viewability repetida, open duplo, session ended, evento orgânico, múltiplas versões/estratégias | SQL/projeções = valores calculados independentemente; resultado idempotente em reprocessamento |
| Cohorts | timestamps no limite do dia local, timezone alternativo, D7 cohort ainda não maduro | exclusão de cohort imaturo, ausência não vira zero |
| Integrity | falha no sink outbox, retry, reordenação e duplicação, restart | eventos não duplicados, lag e alertas corretos |
| Sampling | nenhuma atividade/10 leitores/20 leitores | vazio/supressão/visível conforme política |
| Permissions | produto vs plataforma; analyst vs operator vs admin; book/publisher não autorizado | \`403\` backend, não apenas menu oculto |
| Operation | parar recommendation, invalidar JWT S2S, travar análise, aumentar 5xx, criar backlog | detectar e correlacionar alerta, métrica, trace e log |
| Instrumentation | séries e labels depois de carga/scrape real | nenhum ID pessoal nos labels; budgets e cardinalidade revisados |
| Nonfunctional | 100 queries concorrentes (configurável), 7d/90d/366d, top-N e filtros | timeout/custo definidos após benchmark, p95 documentado |
| UX | desktop 1440px, tablet 768px, mobile 390px, keyboard, screenreader | sem corte de gráficos; labels, legendas, navegação funcionais |
| Deploy | restart Grafana/Prometheus, re-provisionamento do mesmo JSON, backup/restore | dados persistem onde previsto e dashboards são idempotentes |

**Metas propostas a verificar com benchmark real:** API reporting p95 <= 2s para intervalos <=90d e <=5s até 366d; página inicial <=3s após auth em dataset de ensaio; exporter CPU/RAM e armazenamento definidos conforme medições. Não aceitar meta como cumprida sem ensaio reproduzível.

## SLOs e alertas candidatos (valores iniciais não acordados)

- HTTP 5xx / requests > 1% por 10 min com volume mínimo e health probes.
- \`up=0\` por 2 min para serviço crítico; separar maintenance planejada.
- p95 feed > 2.5s por 15 min sob tráfego suficiente; **ajustar conforme baseline real**.
- Outbox com mais de 100 pendentes ou item > 10 min (e sink habilitado), calibração por volume.
- Worker \`RUNNING\` sem progresso > stale-after configurado + grace.
- Ingestão/reporting com watermark > 15 min para agregados near-real-time; diário conforme calendário.
- Disco > 80% sustentado, memory/OOM, PostgreSQL connections > 80% do pool, SeaweedFS indisponível.

Alertas usam \`for\`, severidade, owner, painel, runbook e correlação com deploy; não enviar payload sensível na notificação. Monitorar falsos positivos e ajustar thresholds a SLOs aprovados.

## Processo de entrega Codex

1. Abrir Beads para cada \`dash-XX\` e registrar dependências, branch e estimativas **sem inventar duração**.
2. Dividir PRs: \`docs/architecture\`, \`infra/observability\`, \`instrumentation\`, \`data-quality\`, \`reporting\`, \`admin-ui\`, \`production-readiness\`.
3. Para cada PR: atualizar contratos de API, migrations novas, README/TechDocs, testes, benchmarks e screenshots, executar \`scripts/validate-change.sh --ci\`, \`scripts/validate-pipelines.sh\`, builds/tests dos módulos impactados.
4. Evidência Jenkins deve apontar branch/commit exatos; passar CI não demonstra conexão em runtime; exigir smoke autenticado e read-only DB.
5. Reportar por painel seu status \`AVAILABLE|PARTIAL|NOT_INSTRUMENTED|BLOCKED\` e data source, não fazer rollout silencioso de dados fictícios.
6. PR final documenta riscos, recursos, custo de manutenção, retenção, segurança, runbook, ownership e GO/NO-GO.
