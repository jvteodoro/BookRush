# Jobs persistentes de ingestão

O endpoint administrativo cria uma execução durável no PostgreSQL. A seleção inicial é explícita por identificadores Gutenberg; URLs arbitrárias não são aceitas. A aquisição e os processadores consomem as tarefas persistidas em `catalog.ingestion_task` nos marcos seguintes.

```http
POST /api/admin/v1/ingestion/run
Idempotency-Key: gutenberg-1342-v1
Content-Type: application/json

{"source":"GUTENBERG","externalIds":["1342"],"languages":["en"],"maxItems":1,"dryRun":false,"processAssets":true}
```

O primeiro envio retorna `202 Accepted`, `jobId` e `Location`. Repetir a mesma chave com o mesmo corpo retorna o mesmo job; reutilizá-la com outro corpo retorna `409 IDEMPOTENCY_CONFLICT`. A chave é escopada pelo principal e pelo endpoint.

Use `GET /api/admin/v1/ingestion/jobs/{jobId}` para consultar contadores e status, e `GET /api/admin/v1/ingestion/jobs/{jobId}/items?page=0&size=100` para consultar itens e tarefas. `POST .../cancel` solicita cancelamento durável e `POST .../resume` reabre itens cancelados ou falhos. O estado sobrevive ao reinício porque não depende de memória do processo.

O `scheduler-enabled` continua desligado por padrão. A criação manual do job não depende do scheduler periódico; ela apenas enfileira tarefas com chave operacional determinística. O dispatcher e os handlers de aquisição/processamento serão conectados nos marcos Gutenberg e processamento.
