# API administrativa

`POST /api/v1/admin/audit` registra ação de administração sem armazenar tokens; `GET /api/v1/admin/audit` consulta os últimos eventos. O subject vem da identidade autenticada.

## Usuários

`GET /api/v1/admin/users?search=&page=0&size=25` pesquisa e pagina todas as
contas do realm de produto, inclusive aquelas sem perfil local. Somente tokens
do realm de plataforma com os papéis canônicos `platform-admin` ou
`platform-operator` — ou com os grupos legados `platform-admins` e `operators`
— podem chamar a rota.

`POST /api/v1/admin/users/{subjectKey}/disable` e `/enable` recebem
`{"reason":"..."}`. A alteração é confirmada no Keycloak antes da auditoria;
desabilitar também revoga as sessões do leitor. Motivos vazios, paginação fora
dos limites e tentativa de alterar a própria identidade são rejeitados.

## Moderação

`GET /api/v1/admin/moderation/comments` lista comentários pendentes ou já
decididos. `POST /api/v1/admin/moderation/comments` recebe `commentId` e
`visibility` (`VISIBLE`, `HIDDEN` ou `REMOVED`) e registra a decisão somente
para usuários com papel de operador.
