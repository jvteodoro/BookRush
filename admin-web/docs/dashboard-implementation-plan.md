# Implementação dos dashboards administrativos — issue #8

A arquitetura de observabilidade existente foi preservada. A implementação V2 está organizada nas seis áreas descritas na [especificação](dashboard-specification.md); os 36 painéis, suas definições, fontes e lacunas estão no [catálogo](dashboard-panel-catalog.md).

## Evidências e limites

- O Admin Web consulta somente a área selecionada usando React Query. Dados indisponíveis e freshness desconhecida permanecem explícitos.
- O overview de operações Grafana usa janela padrão de seis horas e agrega tráfego por serviço. Service Detail, PostgreSQL, JVM & Containers, Gateway & Keycloak, Storage & Redis e Logs & Traces reutilizam as fontes já provisionadas.
- A origem autenticada classifica eventos novos. `identity_subject` continua igual ao identificador técnico consumido pelo PFV1. Eventos históricos sem classificação são `UNKNOWN`.
- Smoke journeys periódicos com população TEST, métricas de sucesso funcional, linking completo de feature versionada com outcomes e algumas medidas de conversão por coorte ainda não têm fonte operacional validada. Ver a coluna Estado no catálogo.

## Validação para concluir a entrega

A rodada local deve cobrir build React, testes focados dos serviços alterados, fixtures determinísticas para as seis páginas, renderização responsiva e parse dos JSON Grafana/OpenAPI. Migration, runtime Grafana, smoke autenticado pelo gateway público e evidência em produção dependem do ambiente integrado e não são inferidos a partir de validações locais.

A entrega permanece vinculada à [issue #8](https://github.com/jvteodoro/BookRush/issues/8) e deve ser publicada em um único PR.
