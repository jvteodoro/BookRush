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

## Busca de pessoas

`GET /api/v1/profile/search?q=nome&page=0&size=20` usa o bearer de produto
(realm `bookrush`, audience `account`, sem scope adicional). A rota existente
`PathPrefix(/api/v1/profile)` do Traefik atende também a busca. O frontend chama
pela mesma origem; nenhuma origem CORS nova foi adicionada.

A busca compara substrings literais sem distinguir maiúsculas/minúsculas no
nome de exibição ou username; aceita `@` inicial, no máximo 100 caracteres,
páginas 0–10000 e tamanho 1–50. Uma consulta vazia retorna uma página vazia.
A ordem por nome e subject é determinística e contagem/página usam o mesmo
snapshot. A resposta contém apenas identificador opaco, nome e username;
não consulta a API administrativa do Keycloak nem expõe emails ou biografias.

Somente perfis `PUBLIC` com `is_public=true` são pesquisáveis. `FOLLOWERS` e
`PRIVATE` ficam fora da descoberta, inclusive quando uma flag legada discordar.
A migration V4 adiciona `profile.username`, sem alterar migrations antigas.
Ao ler o próprio perfil autenticado, o serviço sincroniza `preferred_username`
e preenche nome ausente com `name`/username do token, preservando nomes
personalizados e toda a política de privacidade. Perfis antigos tornam-se
pesquisáveis pelo username após seu próximo acesso; nomes já gravados continuam
pesquisáveis imediatamente. A busca retorna somente perfis conhecidos do serviço.

### Validação e deploy

`bash scripts/test-profile-search-e2e.sh` constrói a imagem real, executa Maven
verify e usa PostgreSQL 16, Keycloak 25 e as rotas versionadas do Traefik em uma
rede descartável. Valida boot vazio, upgrade V3 → V4 com dados e privacidade,
segundo boot/Flyway, tokens reais, rejeição de autenticação inválida, busca
literal, paginação e privacidade. O teste participa dos contratos Jenkins e
remove somente containers, rede e tags únicos da própria invocação.

A imagem de perfis já integra o build/deploy Compose; nenhuma variável de
produção, origem, cliente Keycloak ou porta nova é necessária. O teste usa
direct grants somente no realm descartável; o login de produção continua PKCE.
O smoke de produção deve repetir a pesquisa e abertura de perfil pelo gateway
público com token de produto novo após publicar a branch.
