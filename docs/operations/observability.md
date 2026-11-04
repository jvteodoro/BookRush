# Runbook de observabilidade

## Métricas Micrometer

Os serviços Spring Boot que usam `bookrush-observability-spring-boot-starter`
recebem o registry Prometheus e convenções comuns de baixa cardinalidade. O
endpoint interno é `/actuator/prometheus`; ele é permitido sem bearer somente
na rede Docker privada e não é publicado pelo reverse proxy. Os demais
endpoints de Actuator continuam sujeitos à segurança do serviço. As séries HTTP usam histogramas com objetivos
iniciais de 100 ms, 250 ms, 500 ms, 1 s, 2 s e 5 s para permitir P50/P95/P99
agregáveis no Prometheus.

O starter também habilita tracing Micrometer compatível com W3C, exportação
OTLP HTTP para o Alloy e um encoder JSON no console com correlação e contexto
de trace. A amostragem e o endpoint são configuráveis por ambiente; falhas de
telemetria não impedem o serviço de iniciar.

## Dashboards Grafana provisionados

Os dashboards versionados ficam em
`infrastructure/observability/grafana/dashboards/` e são carregados pelo
provider `BookRush versioned dashboards`. A primeira entrega cobre OP-01..06,
PL-01..05, FD-06/07/11 e DQ-01/02/05. As consultas usam Prometheus, e os
datasources Loki/Tempo permanecem disponíveis para drill-down operacional.

Quando a origem de negócio ainda não existe, o painel mostra
`NOT_INSTRUMENTED`; nenhum zero é fabricado. Attribution, outbox, shadow
overlap e freshness dependem das projeções dos pacotes dash-07..10.

As tags comuns são somente `service` e `environment`. Não adicione subject,
user ID, book ID, excerpt ID, token ou texto de livro como tag. Métricas de
produto/recomendação devem usar contadores já versionados e atributos
agregados, não criar uma série por entidade.

## Target down

Verifique `docker compose ps`, o healthcheck do serviço e a conectividade na
rede privada `bookrush_bookrush`. Não exponha o endpoint do exporter para
contornar uma falha de rede. Depois de corrigir a causa, confirme `up == 1` no
Prometheus e o dashboard correspondente.

## SSO e RBAC do Grafana

O Grafana fica sem login anônimo e sem cadastro aberto. O OAuth Keycloak é
opt-in (`OBS_GRAFANA_OIDC_ENABLED=false` por padrão) e usa o realm
`bookrush-platform`. O client, segredo, redirect URI e grupos devem ser
provisionados fora do Git; `platform-admins`, `operators` e Viewer precisam
ser testados em ambiente descartável antes de habilitar o client. O backend de
reporting mantém sua própria autorização e não confia no menu do Grafana ou do
Admin Web.

## HTTP errors

Use as recording rules `bookrush:http_server_errors:rate5m` e
`bookrush:http_requests:rate5m`. Separe health probes de tráfego de negócio,
confirme a rota e o deploy no Jenkins e siga o serviço responsável. Não trate
um erro de autenticação do gateway como indisponibilidade do serviço sem
confirmar o issuer/audience.

## Stuck worker e freshness

A stack de observabilidade não inventa estado de jobs. O alerta só deve ser
ativado quando os exporters/projeções dos pacotes de analytics e reporting
publicarem watermark e progresso. Enquanto a fonte não existir, o painel deve
exibir `NOT_INSTRUMENTED`, não zero.

Os alertas `BookRushTargetDown`, `BookRushHttpServerErrors`,
`BookRushHttpLatencyP95High`, `BookRushBehaviorProjectionStale`,
`BookRushOutboxBacklogOld`, `BookRushReportingProjectionStale` e
`BookRushReportingProjectionError` usam thresholds iniciais, não SLOs
aprovados. Cada alerta tem owner, duração e runbook; confirme o watermark e o
erro do projection health antes de reprocessar ou reiniciar um serviço.

## Backup e restauração

Faça backup consistente dos volumes Grafana, Prometheus, Loki e Tempo conforme
a janela de retenção aprovada. Teste restauração em volumes descartáveis antes
de substituir o volume de produção; nunca remova os volumes da aplicação para
recuperar a stack de observabilidade.
