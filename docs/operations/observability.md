# Runbook de observabilidade

## Target down

Verifique `docker compose ps`, o healthcheck do serviço e a conectividade na
rede privada `bookrush_bookrush`. Não exponha o endpoint do exporter para
contornar uma falha de rede. Depois de corrigir a causa, confirme `up == 1` no
Prometheus e o dashboard correspondente.

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

## Backup e restauração

Faça backup consistente dos volumes Grafana, Prometheus, Loki e Tempo conforme
a janela de retenção aprovada. Teste restauração em volumes descartáveis antes
de substituir o volume de produção; nunca remova os volumes da aplicação para
recuperar a stack de observabilidade.
