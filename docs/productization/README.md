# Productização do BookRush

A plataforma foi organizada em bounded contexts independentes. O catálogo continua dono dos metadados bibliográficos; o analytics continua dono de conteúdo analítico. Os serviços de produto usam referências HTTP e schemas próprios.

## Serviços

| Serviço | Responsabilidade | Schema |
|---|---|---|
| book-content-service | metadata/content reader boundary | `book_content` |
| reader-state-service | library, progress, bookmarks | `reader_state` |

As rotas de estado do leitor são protegidas pelo realm de produto (`bookrush`)
com audience `account`. O gateway envia somente `/api/v1/reader/feed` para o
`reader-bff-service`; biblioteca, progresso e bookmarks seguem diretamente para
o `reader-state-service`.
| reader-profile-service | profile/privacy | `reader_profile` |
| social-service | likes, comments, follows/report extension | `social` |
| behavior-service | append-only client/server events | `behavior` |
| recommendation-service | heuristic-v1 feed ledger contract | `recommendation` |
| reader-bff-service | composição futura de APIs do leitor | sem banco |
| publisher-service | publisher workflow boundary | `publisher` |
| admin-service | administration boundary | `admin` |

Cada serviço tem Dockerfile, health endpoint, Flyway owner, `catalog-info.yaml` e TechDocs. O Compose cria as redes e aguarda PostgreSQL; Traefik roteia apenas APIs internas através do gateway.

## Contratos principais

- `GET /api/v1/books`: catálogo público paginado, com `q`, `page` e `size`.
- `GET /api/v1/recommendations/feed`: feed `heuristic-v1` com `recommendationRequestId`, `impressionId`, `modelVersion` e `rank`.
- `PUT/DELETE /api/v1/reader/library/{bookId}` e `PUT /api/v1/reader/books/{bookId}/progress`: estado persistente por identidade JWT.
- `POST /api/v1/behavior/events`: lote idempotente por `eventKey`; não armazena tokens ou texto livre por padrão.
- `GET /api/v1/behavior/operations/metrics`: métricas operacionais de ingestão, rejeições e outbox; a rotina de manutenção reconstrói projeções diárias e aplica retenção configurável.
- `PUT /api/v1/social/books/{bookId}/like`, `POST /api/v1/social/books/{bookId}/comments`: interação persistente.
- `GET /api/v1/social/moderation/reports` e `PUT /api/v1/social/moderation/comments/{commentId}`: fila e decisão de moderação, restritas a operadores.
- `GET /api/v1/recommendations/feed`: seleciona `COLD_START`, `DIVERSE` ou `LUCKY` e registra request/impression no ledger.
- `GET /api/v1/publisher/review`, `POST /api/v1/publisher/submissions/{id}/review` e `GET /api/v1/publisher/memberships`: workflow de revisão e membership do publicador.
- `GET /api/v1/admin/moderation/comments` e `POST /api/v1/admin/moderation/comments`: consulta e decisão administrativa sobre comentários.
- `GET/PUT /api/v1/profile`: perfil por `(iss, sub)` exposto pela camada de segurança.

As migrations de behavior criam tentativas/reprocessamento de outbox, índices
de projeção e atribuição de resultados. As migrations de publisher criam a
membership explícita (`PENDING`, `ACTIVE`, `SUSPENDED`, `REVOKED`).
O job agendado de behavior reconstrói as projeções e aplica retenção; o
watermark local da outbox não representa publicação em broker externo, que
continua sendo uma etapa operacional separada.

## Execução

```bash
docker compose -f infrastructure/compose.yaml --env-file .env build 
docker compose -f infrastructure/compose.yaml --env-file .env up -d postgres
docker compose -f infrastructure/compose.yaml --env-file .env up -d book-content-service reader-state-service reader-profile-service social-service behavior-service recommendation-service
```

Os modelos e o catálogo não são copiados entre schemas. Antes de habilitar o feed em produção, executar os testes de contrato e configurar o cliente Keycloak correspondente; bulk e treinamento continuam desligados por padrão.

Os contratos OpenAPI de admin, publisher e BFF ficam versionados em cada diretório `api/` e são copiados para a imagem do Backstage pelo Dockerfile raiz.

Web e mobile enviam ações de biblioteca e eventos de abertura através de endpoints autenticados; a identidade nunca é derivada de email ou armazenamento local de credenciais.

O SDK web `frontend/src/telemetry.ts` mantém uma fila em memória, envia lotes para o behavior-service e reencaminha eventos quando o envio falha; tokens não entram na fila.

O portal administrativo agora usa `VITE_USE_MOCKS=false` por padrão; dados de catálogo, ingestão e analytics devem vir das APIs autenticadas.

## Pendências

Consulte o [relatório de pendências](remaining-work.md) para o estado atual dos beads, critérios faltantes e ordem recomendada de conclusão.

O ledger de recomendação persiste request/impression no próprio schema `recommendation`; o behavior-service mantém rejects separados e não mistura content analytics com comportamento.

Conteúdo aprovado pode ser descoberto pelo endpoint reader-assets do catálogo; somente URLs temporárias são retornadas e o binário permanece no storage privado.
