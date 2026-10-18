# Histórico de duração das fases

A migration `V22__analytics_phase_timings.sql` cria a tabela
`analytics.analysis_phase_timing`. Ela registra uma amostra por fase executada
para cada item do batch, incluindo tentativa, job, execução analítica, estado,
horários, duração em milissegundos e um código de erro resumido.

As fases são aditivas e não alteram as tabelas históricas de jobs ou runs. Os
índices suportam as duas consultas operacionais principais: distribuição por
fase em um job específico e distribuição agregada por fase/estado para
dashboards e investigação de regressões.

O histórico é a fonte de auditoria da duração efetiva. O endpoint Prometheus
expõe a visão de observabilidade em tempo de execução; os percentis publicados
por ele são adequados para alertas de latência, enquanto a tabela permite
recalcular períodos históricos após a execução de um batch.

## Consulta para dashboards

Use `GET /analytics/api/admin/v1/content-analytics/phase-timings` com um token
que tenha `bookrush.analytics` e, opcionalmente, `jobId` e `limit` (1--1000).
Cada linha combina `phase` e `status` e contém `samples`, `mean_ms`, `min_ms`,
`max_ms`, `p50_ms`, `p90_ms`, `p95_ms` e `p99_ms`. Para Grafana, prefira
`p95_ms`/`p99_ms` para alertas e `samples` para evitar conclusões baseadas em
uma única execução. A rota exige autenticação administrativa; respostas 401 e
403 devem ser tratadas como falha de autorização, não como ausência de dados.

Exemplo de agrupamento para uma tabela: filtrar `phase = EXCERPT_EMBEDDINGS`,
ordenar por `p95_ms` decrescente e separar `COMPLETED` de `FAILED`. O endpoint
é histórico e não substitui o heartbeat/estado do job; use a API de jobs para
acompanhar progresso corrente.
