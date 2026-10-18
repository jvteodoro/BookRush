# reader-profile-service

Reader profile and privacy preferences. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

## Privacidade

As preferências de visibilidade são persistidas em `reader_profile.profile_privacy`
e pertencem ao sujeito autenticado. O frontend usa o mesmo bearer token do
login do produto:

- `GET /api/v1/profile/privacy` lê a política atual;
- `PUT /api/v1/profile/privacy` grava os campos `profileVisibility`,
  `activityVisibility`, `libraryVisibility`, `recentVisibility` e
  `socialGraphVisibility`.

Cada campo aceita `PUBLIC`, `FOLLOWERS` ou `PRIVATE`. A rota não aceita usuário
anônimo e mantém `profile.is_public` sincronizado com `profileVisibility` para
preservar compatibilidade com a consulta pública existente.

O contrato está em `api/openapi.yaml`. A origem do browser e as authorities são
as mesmas do restante do serviço; não há bypass de autenticação nem origem CORS
curinga.
