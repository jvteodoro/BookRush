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

## Remover o próprio comentário

`DELETE /api/v1/social/comments/{commentId}` retorna 204 sem corpo, inclusive
quando repetido pelo mesmo autor. Exige token do realm de produto `bookrush`,
issuer e audience configurados do serviço. O subject vem do token e deve ser
igual a `social.comments.subject_key`: token ausente/inválido/expirado recebe
401, outro autor recebe 403 e UUID inexistente recebe 404. O método DELETE e os
cabeçalhos Authorization/Content-Type já fazem parte do CORS explícito; o
reverse proxy mantém o prefixo `/api/v1/social/` ao encaminhar ao social-service.

A listagem devolve `canDelete` calculado no servidor; nomes de apresentação não
concedem autorização. A interface solicita confirmação e atualiza lista e
contagem somente após o sucesso. Conteúdo, autoria e denúncias permanecem
preservados para moderação. [Contrato OpenAPI](../api/openapi.yaml).

Em rollout parcial, implante primeiro o social-service para aplicar V8 e depois
o admin-service/admin-web, que exibem a remoção pelo autor e bloqueiam restauração.
A pipeline integrada já constrói e implanta esses serviços.

## Contagem de seguidores

`GET /api/v1/social/followers` usa o subject do token para consultar a própria
contagem; `?user=<subject>` consulta outro perfil existente. Responde `user`,
`following`, `visible` e `followerCount` inteiro quando visível. Se a privacidade
não permitir acesso, `visible=false` e o número é omitido/nulo, nunca um zero
fictício. O autor vê sua própria contagem mesmo com perfil privado ou sem perfil
persistido. Outra identidade inexistente recebe 404; ausência de token recebe
401. Issuer/audience de produto e CORS GET permanecem os mesmos do serviço.

A leitura respeita `profileVisibility` e `socialGraphVisibility` da configuração
do reader-profile-service: PUBLIC para leitores autenticados; FOLLOWERS para
quem segue o perfil; PRIVATE apenas para o próprio leitor. O flag legado
`is_public=false` também bloqueia um perfil que ainda esteja marcado PUBLIC na
projeção de privacidade. Somente agregados são retornados, sem lista de pessoas.
A consulta usa snapshot consistente e o índice V9 por `followed`. A chave
primária existente impede que seguir repetidamente infle a contagem. O frontend
consulta novamente após seguir/desseguir e apresenta indisponibilidade em caso
de erro. Não há contadores duplicados nem novas dependências de runtime.

## Avaliações de livros

`GET/PUT/DELETE /api/v1/social/books/{bookId}/ratings` pertence ao social-service
e atravessa a rota `/api/v1/social` existente no Traefik. Exige bearer do realm
produto `bookrush`, audience `account`, sem scope adicional. O subject do token
é a única identidade aceita: PUT cria/substitui a própria nota inteira de 1 a 5,
DELETE remove apenas a própria nota e é idempotente. GET retorna `bookId`,
`totalRatings`, `averageRating` arredondada a duas casas e `myRating`, quando
existem. Sem notas, média e nota pessoal são omitidas. Nenhuma identidade alheia
é exposta; notas de perfis privados contribuem somente ao agregado anônimo.

O catálogo valida a existência do livro por GET público, sem encaminhar bearer
de produto a endpoints do realm de plataforma. UUID/nota inválidos retornam 400,
ausência de autenticação 401, livro ausente 404 e catálogo indisponível 503.
Origens CORS explícitas em `BOOKRUSH_CORS_ALLOWED_ORIGINS`; PUT/DELETE e
Authorization/Content-Type já permitidos. `CATALOG_URL` no serviço usa
`http://catalog-service:8080` por padrão (`SOCIAL_CATALOG_URL` no Compose).

Validação isolada: `bash scripts/test-book-ratings-e2e.sh` executa Maven verify,
imagens reais, PostgreSQL 16, upgrade V9→V10, JWTs de dois leitores e chamadas
pelo gateway versionado, incluindo CORS, concorrência, edição e exclusão.
Recursos do teste são descartados, sem usar `.env` ou volumes de produção.
