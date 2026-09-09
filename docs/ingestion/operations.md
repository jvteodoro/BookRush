# Operação do microserviço de ingestão

O serviço roda em `book-ingestion-service` no Compose, usa PostgreSQL para filas e leases, SeaweedFS para RAW e o `catalog-service` para comandos canônicos. O endpoint `/actuator/health` é o healthcheck. Tarefas expiradas podem ser inspecionadas em `GET /api/admin/v1/operations/stale-tasks` e reencaminhadas com `POST /api/admin/v1/operations/requeue-expired`.

Antes de habilitar scheduler, configure `INGESTION_ROLE`, limites de tamanho, allowlist de fontes e credenciais S3. O modo padrão é manual (`INGESTION_SCHEDULER_ENABLED=false`). Snapshots RAW são imutáveis; retenção e reconciliação devem ser executadas após confirmar que nenhuma tarefa possui lease ativo.
