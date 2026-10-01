# Runbook

1. Verifique health e banco disponível.
2. Consulte logs de book-ingestion-service; preserve o primeiro erro antes de reiniciar.
3. Submeta lote pequeno autenticado, começando com dryRun; consulte job e itens. Depois valide hashes e presença S3. Resume deve respeitar idempotência.

## Upload de publisher

O `publisher-service` chama `POST /api/internal/v1/ingestion/publisher-submissions/process`
após um upload finalizado. A chamada é somente interna e exige
`X-BookRush-Ingestion-Token`. O worker verifica o hash do objeto no bucket de
staging, grava um asset `ADMIN_UPLOAD` no catálogo e chama o callback do
publisher. Se o processamento falhar, o objeto permanece privado para retry;
não remova o staging manualmente antes de investigar os logs do submission ID.
4. Execute rollback de imagem somente se compatível com o schema; não remova volumes.

Detalhes operacionais estão no handbook storage/operations e ingestion/runbook.
