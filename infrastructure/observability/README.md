# Observabilidade BookRush

Esta stack é um Compose separado para a observabilidade privada do BookRush.
Ela não publica exporters, Prometheus, Loki, Tempo ou Alloy na internet. O
único bind de host é o Grafana em `127.0.0.1`, protegido por credencial local
até a integração SSO do pacote `dash-06`.

## Componentes

- Prometheus: métricas, recording rules e alertas iniciais.
- Grafana: datasource provisionado para Prometheus, Loki e Tempo.
- Loki: logs estruturados com retenção limitada.
- Tempo: traces OTLP e service graph.
- Alloy: entrada OTLP para traces/logs e encaminhamento privado.
- node-exporter/cAdvisor: host, containers, CPU, memória, disco e rede.
- postgres-exporter/redis-exporter: métricas de dependências internas.
- DQ-02: `bookrush_behavior_outbox_*` mede backlog, idade do item mais antigo,
  tentativas falhas e publicações recentes a partir de `behavior.outbox`.
- DQ-05: `bookrush_reporting_projection_*` expõe status, timestamp da última
  atualização e quantidade materializada por projeção, com a etiqueta de
  baixa cardinalidade `projection`.
- Feed: `bookrush_feed_*` expõe caminhos de personalização, candidatos,
  itens, confiança de perfil e comparação shadow; os painéis não interpretam
  esses contadores operacionais como conversão ou causalidade.

As imagens são fixadas por versão. Os volumes `prometheus_data`,
`grafana_data`, `loki_data` e `tempo_data` são persistentes e independentes dos
volumes da aplicação.

## Configuração protegida

Defina no ambiente protegido, sem versionar o arquivo:

```dotenv
OBS_GRAFANA_ADMIN_PASSWORD=<senha protegida>
OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME=postgresql://<usuario>:<senha>@postgres:5432/bookrush?sslmode=disable
BOOKRUSH_NETWORK_NAME=bookrush_bookrush
OBS_GRAFANA_OIDC_ENABLED=false
OBS_GRAFANA_OIDC_CLIENT_ID=bookrush-grafana
# OBS_GRAFANA_OIDC_CLIENT_SECRET=<protected secret>
```

O DSN do exporter não deve ser colocado em logs, Beads, argumentos de jobs ou
no navegador. O valor padrão de rede presume o Compose principal do BookRush;
para validação isolada, crie uma rede com o mesmo nome ou use um override
descartável.

## Inicialização e validação

```bash
docker compose --env-file /run/bookrush-observability.env \
  -f infrastructure/observability/compose.yaml config --quiet
docker compose --env-file /run/bookrush-observability.env \
  -f infrastructure/observability/compose.yaml up -d
docker compose --env-file /run/bookrush-observability.env \
  -f infrastructure/observability/compose.yaml ps
```

Antes de conectar a produção, validar em ambiente descartável a carga das
configurações, os healthchecks, o provisionamento idempotente, a retenção e o
backup/restore dos quatro volumes. A integração com o Compose principal e o
SSO Keycloak permanecem nos pacotes seguintes.

Quando habilitado, o OAuth genérico do Grafana usa o realm
`bookrush-platform`; `platform-admins` recebe Admin, `operators` recebe Editor
e os demais usuários autenticados recebem Viewer. O backend de reporting deve
repetir a autorização: esconder o menu no Admin Web não é controle suficiente.
Não habilite essa opção antes de validar redirect URI, grupos e revogação em
ambiente descartável.

## Limites e segurança

Os limites de CPU/memória estão declarados por serviço. Labels de Prometheus
devem permanecer em baixa cardinalidade: nunca usar subject, user ID, book ID,
excerpt ID, token, texto integral ou URL assinada como label. Traces e logs
devem aplicar o mesmo princípio antes de serem encaminhados ao Alloy.
