# publisher-service

Publisher submissions and authorized metrics. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Consulte [API](api.md) para submissões.

Uma submissão pode avançar para `SUBMITTED` usando `POST /api/v1/publisher/submissions/{id}/submit`.

Submission ownership is scoped to the authenticated principal. Anonymous
requests are rejected before any database write, and retrying a submit only
changes the caller's own submission.

The service validates the `bookrush-platform` issuer/JWK set and requires the
`bookrush-ingestion` audience. OAuth scopes and Keycloak groups are converted
to `SCOPE_*` and `ROLE_*` authorities before publisher/operator actions run.

Criação e submissão de publicação emitem fatos server-side ao behavior-service
com o bearer autenticado, chave idempotente e retry limitado. A telemetria é
best effort e não altera o ownership nem o estado transacional da submissão.

## Perfil local e healthcheck

Com `BOOKRUSH_SECURITY_ENABLED=false`, uma cadeia local explícita permite o
healthcheck do Compose sem bearer; o perfil protegido mantém a validação JWT,
issuer, audience e autorização das rotas de publicação.
