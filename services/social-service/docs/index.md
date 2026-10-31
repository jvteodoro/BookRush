# social-service

Likes, follows, comments and reports. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Também são suportados follows e reports; reports entram como registros OPEN para moderação.
Comentários podem ser denunciados pelo leitor em `POST /api/v1/social/reports`,
informando `bookId`, `commentId` e o motivo. O serviço valida a existência do
comentário antes de adicioná-lo à fila exibida no Admin Console.
O ciclo administrativo usa `OPEN`, `IN_REVIEW`, `RESOLVED` e `DISMISSED`;
decisões terminais guardam operador, instante e justificativa, além do registro
imutável no audit log do admin-service.

Migration V3 adiciona follows e reports, mantendo report como fila de moderação.

As mutações de like, follow, report, share e comentário também emitem fatos
server-side ao behavior-service. A entrega usa o bearer autenticado, chave
única por evento e três tentativas; falha de telemetria não desfaz a mutação
social já persistida.

As rotas de moderação `GET /api/v1/social/moderation/reports` e
`PUT /api/v1/social/moderation/comments/{commentId}` exigem os grupos
`operators` ou `platform-admins` do realm `bookrush-platform`. A política CORS
aceita somente as origens configuradas em `BOOKRUSH_CORS_ALLOWED_ORIGINS`.
The service validates the `bookrush-platform` issuer/JWK set and requires the
`bookrush-ingestion` audience. It maps OAuth scopes and the Keycloak `groups`
claim to `SCOPE_*`/`ROLE_*` authorities for moderation operations.

## Validade dos tokens e moderação administrativa

A validação JWT preserva os validadores padrão de expiração e not-before junto
com issuer e audience. Comentários ocultos/removidos pela fachada administrativa
continuam excluídos da listagem de leitores; restaurar devolve a visibilidade.
O teste integrado `scripts/test-admin-user-management-e2e.sh` verifica esse
comportamento contra os serviços reais e PostgreSQL descartável.
