# reader-profile-service

Reader profile and privacy preferences. Este serviço é um bounded context
independente; contratos e ownership estão no ADR 0034.

## API de perfil e privacidade

As rotas públicas passam pelo gateway em `/api/v1/profile` e pertencem a este
serviço. Ambas exigem bearer do realm de produto `bookrush`, com issuer e
audience configurados pelas propriedades `BOOKRUSH_SECURITY_*`.

- `GET /api/v1/profile` retorna o perfil autenticado e as cinco audiências;
- `PUT /api/v1/profile` atualiza parcialmente perfil e preferências;
- `GET /api/v1/profile/public?subject=...` expõe nome e bio somente quando
  `profileVisibility=PUBLIC`.

Cada preferência aceita `PUBLIC`, `FOLLOWERS` ou `PRIVATE`:

| Campo | Padrão | Conteúdo protegido |
| --- | --- | --- |
| `profileVisibility` | `PUBLIC` | Perfil e biografia |
| `activityVisibility` | `PUBLIC` | Curtidas, comentários e atividade |
| `libraryVisibility` | `PRIVATE` | Biblioteca salva |
| `recentVisibility` | `PRIVATE` | Leituras recentes |
| `socialGraphVisibility` | `PUBLIC` | Seguidores e seguindo |

O campo legado `isPublic` permanece compatível e altera apenas
`profileVisibility`. Valores desconhecidos retornam `400`; ausência ou
invalidade do token retorna `401`, e falta de autorização retorna `403`.

O contrato completo está em [`api/openapi.yaml`](../api/openapi.yaml).
