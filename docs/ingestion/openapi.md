# API administrativa (resumo)

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/admin/v1/ingestion/run?dryRun=true` | iniciar uma execução ou apenas validar |
| POST | `/api/admin/v1/ingestion/cancel` | solicitar cancelamento cooperativo |
| GET | `/api/admin/v1/ingestion/state` | consultar estado do bootstrap |
| GET | `/api/admin/v1/operations/stale-tasks` | listar leases expirados |
| POST | `/api/admin/v1/operations/requeue-expired` | reencaminhar tarefas expiradas |

Em produção as rotas exigem OIDC e papel administrativo. Respostas de conflito usam HTTP 409; chamadas são seguras para repetição quando a operação é identificada por chave de idempotência no catálogo.
