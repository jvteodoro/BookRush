# ADR-012 — Fronteiras entre dashboards administrativos, observabilidade e analytics de negócio

- **Estado:** Proposed (submetido a revisão pela issue #8)
- **Data:** 2026-11-04
- **Owners propostos:** Platform Engineering, Product Analytics, Security
- **Contexto:** [issue #8](https://github.com/jvteodoro/BookRush/issues/8); [spec admin](../../admin-web/docs/dashboard-specification.md)

## Contexto

O BookRush possui múltiplos bounded contexts proprietários de schemas, métricas Micrometer com maturidade desigual, ledger persistente de recomendações, fatos comportamentais/outbox, jobs de analytics, controles de Keycloak e uma SPA administrativa. O usuário necessita painéis operacionais de confiabilidade e visões de negócio (cohorts, leitura, desempenho do feed e conteúdo) com fontes e definições auditáveis. Inserir consultas cross-schema diretamente nos serviços de produto ou expor PostgreSQL e Prometheus no navegador quebraria ownership, segurança e capacidade.

## Decisão proposta

1. **Plano operacional:** Grafana OSS com Prometheus para counters/histogramas/gauges de baixa cardinalidade, Loki para logs estruturados, Tempo para traces e Alloy para coleta. Deploy Docker Compose separado, provisionamento Git, rede privada, retenção finita e SSO do realm administrativo Keycloak. Backstage liga dashboards aos respectivos owners/runbooks.
2. **Plano de negócio:** projeções \`reporting\` versionadas, construídas via snapshot/exportações read-only de owners. API administrativa \`/api/admin/v1/reporting/v1\` (facade) consulta somente projeções segregadas, aplica filtros, cotas, autorização e supressão de amostras pequenas. Admin Web apresenta gráficos executivos, de comportamento e modelos, links contextuais a Grafana. Grafana pode consultar agregados via datasource read-only.
3. **Planos separados mas correlacionados:** Métrica Prometheus não carrega user/book/excerpt IDs; rastreabilidade de pessoa/livro fica no sistema analítico com controles de privacidade. Correlation IDs em traces/logs autorizados; web/app não emite tokens para sistemas de observabilidade.
4. **Sem dados enganosos:** todo indicador inclui definição, amostra, corte temporal, versão da projeção, status de frescor e domínio. Sem fonte, \`NOT_INSTRUMENTED\`; baixa amostra, \`INSUFFICIENT_SAMPLE\`. Seed, teste e produção segregados.
5. **Evolução incremental:** integrar nas aplicações existentes antes de um novo serviço; criar serviço dedicado somente se escalabilidade/ownership exigirem, registrada em novo ADR. Não adotar OLAP distribuído nem Kafka sem medições.
6. **Governança:** definição canônica no [catálogo](../../admin-web/docs/dashboard-panel-catalog.md) e [contratos](../../admin-web/docs/dashboard-data-contracts.md); cada nova métrica exige teste e atualização de TechDocs. Os runbooks e o gate constam no [plano](../../admin-web/docs/dashboard-implementation-plan.md).

## Alternativas consideradas

- **Grafana para todos os dashboards, inclusive UX de produto:** menos React, porém filtros/correlação por recurso, branding e autorização por livro/editora mais difíceis; poderá servir exploração e dashboards técnicos, não substituir facade do Admin Web.
- **React acessando diretamente Prometheus, PostgreSQL ou Loki:** rejeitado por privilégio, PII, cardinalidade, SQL arbitrário e exposição do plano de observabilidade.
- **JDBC cross-schema em cada serviço de produto:** rejeitado pelo ADR de bounded contexts e acoplamento das migrations.
- **Novo analytics microservice de imediato:** adiado até existir evidência de carga/ownership; projetar interface desacoplada para futura extração.
- **Somente counters Prometheus para os KPIs:** rejeitado por dimensões de usuário/obra, coortes e atribuição de eventos exigirem fatos persistidos e deduplicados.

## Consequências e riscos

+ Instrumentação e runbooks reutilizáveis, painéis versionados, alertas operacionais e métricas de produto auditáveis.
+ Retenção e ACL separadas, controle de custos por cardinalidade, monitoramento de qualidade de dados.
- Custo de operação adicional (collectors, storage, SSO, backups, dashboards).
- Necessidade de gestão de consistência e atraso de snapshots entre bounded contexts; não alegar frescor em tempo real sem comprovação.
- Risco de reidentificação em segmentos pequenos; regras de supressão/anonimização e auditoria obrigatórias.
- Attribution observacional não prova causalidade; experimentos exigem randomização e assignment.

## Critérios para aceitar o ADR

Revisão Platform + Security + Product; validação da fronteira de dados, ownership, indicadores de negócio, Keycloak e runbooks. Até essa aprovação, o estado permanece **Proposed**, mesmo que a documentação esteja mergeada.
