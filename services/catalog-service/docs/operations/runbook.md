# Runbook

1. Verifique health e banco disponível.
2. Consulte logs de catalog-service; preserve o primeiro erro antes de reiniciar.
3. Antes de migrations faça backup. Em falha de upload confira estado da versão e objeto, preservando SOURCE. Use endpoint administrativo para inspecionar versões.
4. Execute rollback de imagem somente se compatível com o schema; não remova volumes.

Detalhes operacionais estão no handbook storage/operations e ingestion/runbook.
