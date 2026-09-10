# Runbook

1. Verifique health e banco disponível.
2. Consulte logs de book-ingestion-service; preserve o primeiro erro antes de reiniciar.
3. Submeta lote pequeno autenticado, começando com dryRun; consulte job e itens. Depois valide hashes e presença S3. Resume deve respeitar idempotência.
4. Execute rollback de imagem somente se compatível com o schema; não remova volumes.

Detalhes operacionais estão no handbook storage/operations e ingestion/runbook.
