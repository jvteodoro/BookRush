# Contrato canônico interno

O endpoint `POST /api/internal/v1/catalog/commands` pertence ao
`catalog-service`. Ele aceita apenas o token service-to-service configurado em
`CANONICAL_SERVICE_TOKEN`; o token `ASSET_ADMIN` não autoriza esta rota.

Exemplo de comando idempotente:

```json
{
  "operationKey": "record/GUTENBERG/GUTENBERG_ID/1342/1/canonicalize/v1",
  "requestHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "fenceToken": 4,
  "sourceCode": "GUTENBERG",
  "identifierType": "GUTENBERG_ID",
  "identifierValue": "1342",
  "title": "Pride and Prejudice",
  "language": "eng",
  "description": "Public-domain work"
}
```

O catálogo grava `ingestion_command_result` antes de responder. Repetir a
operation key com o mesmo hash retorna o resultado persistido; usar outro hash
retorna `409 CANONICAL_CONFLICT`. Um fence token inferior ao maior token da
operação também retorna 409. A escrita cria ou atualiza a obra e o identificador
externo dentro de uma transação PostgreSQL; não há transação distribuída com o
serviço de ingestão.
