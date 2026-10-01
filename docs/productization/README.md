# Productização do BookRush

A plataforma foi organizada em bounded contexts independentes. O catálogo continua dono dos metadados bibliográficos; o analytics continua dono de conteúdo analítico. Os serviços de produto usam referências HTTP e schemas próprios.

## Serviços

| Serviço | Responsabilidade | Schema |
|---|---|---|
| book-content-service | metadata/content reader boundary | `book_content` |
| reader-state-service | library, progress, bookmarks | `reader_state` |
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
- `PUT /api/v1/social/books/{bookId}/like`, `POST /api/v1/social/books/{bookId}/comments`: interação persistente.
- `GET/PUT /api/v1/profile`: perfil por `(iss, sub)` exposto pela camada de segurança.

## Execução

```bash
docker compose -f infrastructure/compose.yaml --env-file .env build 
docker compose -f infrastructure/compose.yaml --env-file .env up -d postgres
docker compose -f infrastructure/compose.yaml --env-file .env up -d book-content-service reader-state-service reader-profile-service social-service behavior-service recommendation-service
```

Os modelos e o catálogo não são copiados entre schemas. Antes de habilitar o feed em produção, executar os testes de contrato e configurar o cliente Keycloak correspondente; bulk e treinamento continuam desligados por padrão.

Os contratos OpenAPI de admin, publisher e BFF ficam versionados em cada diretório `api/` e são copiados para a imagem do Backstage pelo Dockerfile raiz.
