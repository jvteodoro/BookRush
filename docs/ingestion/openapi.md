# API administrativa (resumo)

## Swagger

Com o gateway ativo, a documentação interativa fica disponível em
`https://bookrush.jteodoro.tec.br/ingestion-docs/swagger-ui.html` e o contrato
OpenAPI em `/ingestion-docs/v3/api-docs`. O Springdoc gera ambos a partir dos
controllers e das anotações `@OpenAPIDefinition`; não há um contrato JSON
duplicado para manter manualmente.

Os contratos anunciam explicitamente os servidores públicos HTTPS configurados
por `BOOKRUSH_PUBLIC_URL` (catálogo) e `INGESTION_PUBLIC_URL` (ingestion,
incluindo o prefixo `/ingestion`). Assim, o botão **Try it out** não tenta
chamar o salto HTTP interno do Docker. As operações são agrupadas por tags e
descrevem autenticação, idempotência, estados de jobs, assets, versões, URLs
pré-assinadas e linhagem de processamento.

| Método | Rota | Finalidade |
|---|---|---|
| POST | `/api/admin/v1/ingestion/run` | criar job persistente; requer `Idempotency-Key` |
| GET | `/api/admin/v1/ingestion/jobs/{jobId}` | consultar status, parâmetros e contadores |
| GET | `/api/admin/v1/ingestion/jobs/{jobId}/items` | listar itens e tarefas paginados |
| POST | `/api/admin/v1/ingestion/jobs/{jobId}/cancel` | solicitar cancelamento cooperativo |
| POST | `/api/admin/v1/ingestion/jobs/{jobId}/resume` | reabrir itens cancelados/falhos |
| POST | `/api/admin/v1/ingestion/cancel` | compatibilidade: cancelar o job mais recente |
| GET | `/api/admin/v1/ingestion/state` | compatibilidade: consultar o job mais recente |
| GET | `/api/admin/v1/operations/stale-tasks` | listar leases expirados |
| POST | `/api/admin/v1/operations/requeue-expired` | reencaminhar tarefas expiradas |

O corpo de `run` está documentado em [jobs-api.md](jobs-api.md). Ele aceita seleção explícita de IDs Gutenberg e não aceita URLs arbitrárias. A primeira resposta é `202` com `jobId` e `Location`; a repetição da mesma chave e corpo retorna o mesmo resultado, enquanto um corpo diferente retorna `409`.

Em produção as rotas exigem OIDC e papel administrativo. Em ambiente local com OIDC desabilitado elas ficam permitidas para desenvolvimento. O scheduler periódico continua opcional; a criação manual persiste a seleção no PostgreSQL.
