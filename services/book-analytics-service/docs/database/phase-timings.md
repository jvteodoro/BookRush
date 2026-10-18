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
