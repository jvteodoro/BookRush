# API administrativa

`POST /api/v1/admin/audit` registra ação de administração sem armazenar tokens; `GET /api/v1/admin/audit` consulta os últimos eventos. O subject vem da identidade autenticada.

## Moderação

`GET /api/v1/admin/moderation/comments` lista comentários pendentes ou já
decididos. `POST /api/v1/admin/moderation/comments` recebe `commentId` e
`visibility` (`VISIBLE`, `HIDDEN` ou `REMOVED`) e registra a decisão somente
para usuários com papel de operador.
