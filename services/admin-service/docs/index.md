# admin-service

Administrative facade and audit operations. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Consulte [API](api.md) para os endpoints de auditoria.

A moderação é registrada em `admin.moderation` com actor subject e decisão auditável.

Migration V3 adiciona o registro de decisões de moderação e endpoints de consulta.

All read and write endpoints require an authenticated administrative principal;
the actor subject is taken from the server security context and never from a
request field.

Além da autenticação, a facade exige a autoridade `ROLE_platform-admins`,
`ROLE_operators` ou `ROLE_OPERATOR`. O starter converte grupos Keycloak
validados da claim `groups` em authorities com o prefixo `ROLE_`; roles de um
realm ou client não confiável não são aceitas pelo decoder permitido.
## Operações administrativas

As rotas `/api/v1/admin/users`, `/reports`, `/moderation/comments` e `/kpis`
exigem token do realm `bookrush-platform` com grupo `operators` ou
`platform-admins`. A alteração de usuários usa o cliente confidencial
`bookrush-admin-service`, com `query-users` e `manage-users` no cliente
`realm-management`; o segredo é fornecido somente por
`KEYCLOAK_ADMIN_CLIENT_SECRET`.

O endpoint `/users/{subjectKey}/disable|enable` grava auditoria e uma linha de
restrição após a confirmação do Keycloak. Sem a configuração do cliente, a API
responde `503` em vez de fingir que a alteração foi aplicada.
The service validates the `bookrush-platform` issuer/JWK set and requires the
`bookrush-ingestion` audience. It maps both OAuth scopes and the Keycloak
`groups` claim to `SCOPE_*`/`ROLE_*` authorities before enforcing operator
actions. This explicit decoder prevents a token issued for another platform
client from being accepted accidentally.

Os logs de autenticação OAuth2 podem ser elevados temporariamente durante um
incidente operacional; o nível padrão de produção permanece sem payloads de
tokens.
