# ADR-004 — Spring Batch, fila operacional e leases

`ingestion_task` é a autoridade única de claims. `LeaseCoordinator` usa
`FOR UPDATE SKIP LOCKED`, relógio PostgreSQL, `lease_until`, heartbeat e token
monotônico. O worker pode fazer HTTP/S3 fora da transação; heartbeat e
finalização usam compare-and-set por `(task_id, claimed_by, fence_token)`.

Uma lease expirada pode ser retomada por outro worker. O primeiro worker ainda
pode terminar I/O, mas recebe `StaleLeaseException` ao tentar confirmar e não
altera a tarefa nem o catálogo. O token também é enviado ao
`CanonicalCatalogPort`, que o verifica no commit canônico.

Spring Batch mantém o JobRepository no PostgreSQL e registra execuções; ele não
substitui a fila operacional nem cria uma segunda autoridade de claims. O
dispatcher consulta tarefas prontas, e cancelamento impede novos claims sem
apagar tarefas já confirmadas.
