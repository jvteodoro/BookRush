# Integration contracts (historical package reference)

The maintained contract is [api-contracts.md](api-contracts.md). The original
package used the generic paths below as a design sketch; production adapters
now use the gateway paths and payloads documented in that page.

Os endpoints abaixo são adapters propostos porque os contratos concretos do backend não estavam disponíveis no contexto acessível. Substitua somente `src/lib/services.ts` ao ligar nos serviços reais.

## Catalog

`GET /admin/books`

Retorna obras candidatas/importadas.

`POST /admin/imports`

```json
{ "bookId": "g-1342" }
```

Resposta recomendada: `202 Accepted` com `{ "jobId": "...", "bookId": "...", "status": "QUEUED" }`.

## Analytics

`GET /admin/analytics/runs`

`POST /admin/analytics/runs`

```json
{ "bookId": "g-84" }
```

O resultado deve incluir versão do pipeline/modelo e métricas.

## Feed training

`POST /admin/training-sessions`

```json
{
  "population": "ADMIN_SEED",
  "weights": {
    "hook": 0.28,
    "novelty": 0.16,
    "affinity": 0.22,
    "readability": 0.12,
    "diversity": 0.10,
    "exploration": 0.12
  }
}
```

O backend deve obter `userId` do JWT e não aceitar um user id arbitrário do browser como fonte de verdade.

`PUT /admin/training-sessions/{sessionId}/parameters`

Cria nova `parameterVersion` imutável.

`GET /admin/feed?sessionId=...`

Retorna itens ranqueados e, em modo debug/admin, features e score decomposition.

`POST /admin/events`

```json
{
  "sessionId": "sess-...",
  "excerptId": "ex-...",
  "type": "LIKE",
  "occurredAt": "2026-09-30T12:00:00Z",
  "parameterVersion": "pv-..."
}
```

O backend deriva o usuário autenticado do JWT. Para DWELL, usar `value` em milissegundos.

## Eventos mínimos

- IMPRESSION
- DWELL
- SKIP
- LIKE
- DISLIKE
- SAVE
- OPEN_BOOK

## Idempotência

Importações e analytics devem aceitar `Idempotency-Key`. Eventos de feed devem aceitar `eventId` UUID para deduplicação quando o aplicativo retransmitir por falha de rede.
