# Runbook de ingestão Gutenberg

Suba PostgreSQL, Redis, SeaweedFS, `catalog-service` e
`book-ingestion-service` pelo Compose. Em produção, habilite o perfil `auth`,
configure o issuer público do Keycloak e mantenha o segredo fora do Git. O
gateway expõe a API em `/ingestion/api/...` e o Swagger em
`/ingestion-docs/swagger-ui.html`.

## Execução controlada

```bash
curl -fsS -X POST "$BOOKRUSH_URL/ingestion/api/admin/v1/ingestion/run" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: gutenberg-1342-dry-run-v1' \
  --data '{"source":"GUTENBERG","externalIds":["1342"],"maxItems":1,"dryRun":true,"processAssets":false}'
```

A resposta `202` contém `jobId`. Consulte o job e os itens até um estado
terminal com `GET .../jobs/{jobId}` e `GET .../jobs/{jobId}/items?page=0&size=100`.
Para importar arquivos, repita com `dryRun:false` e `processAssets:true`,
mantendo a seleção limitada. Repetir a mesma chave e corpo é idempotente;
corpo diferente retorna `409`.

Em `COMPLETED`, confirme `ingestion_item`, `source_record`, `book_asset`,
`book_asset_version` e `asset_processing` no PostgreSQL. Os blobs devem estar
nos buckets privados e ser identificados por `bucket`/`object_key`, com SHA-256
coincidente; nunca publique um asset sem aprovação de direitos.

`PAUSED`, `RETRY_WAIT` e `FAILED` exigem consultar `error_code`, `reason_code`,
`attempt_count` e `next_attempt_at`. Use `POST .../operations/requeue-expired`
para leases expiradas ou `POST .../jobs/{jobId}/resume` para retomar. O
`fence_token` impede confirmações de workers obsoletos.

O estágio Jenkins **Importação Gutenberg (opt-in)** é desabilitado por padrão.
Após um dry-run, marque `RUN_GUTENBERG_IMPORT`; o relatório sanitizado é
arquivado como `gutenberg-import-report.json`. Pushes e deploys normais não
iniciam ingestão.

Não execute `docker compose down --volumes` no projeto de produção. Em falha
pós-upload, preserve `storage_intent` e `book_asset_version` para reconciliação.
