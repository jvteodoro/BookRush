# Segurança e integração Git

Guest é somente para loopback/desenvolvimento. Nunca ative
`dangerouslyAllowOutsideDevelopment` em produção. No perfil público use Keycloak OIDC,
TLS no proxy, sessão secreta e catálogo User com email correspondente. Ausência de
usuário deve negar login; não permitir resolver que crie identidade irrestrita.

## Keycloak

O portal usa o cliente confidencial `bookrush-backstage` no realm
`bookrush-platform`, com Authorization Code e redirect URI exata:
`https://docs-bookrush.jteodoro.tec.br/api/auth/oidc/handler/frame`. A Web Origin
é `https://docs-bookrush.jteodoro.tec.br`; password grant permanece desabilitado.

O Compose recebe `BACKSTAGE_OIDC_ENABLED`, `AUTH_OIDC_METADATA_URL`,
`AUTH_OIDC_CLIENT_ID`, `AUTH_OIDC_CLIENT_SECRET` e `AUTH_SESSION_SECRET` do
`.env` protegido. O entrypoint carrega `app-config.oidc.yaml` somente quando
`BACKSTAGE_OIDC_ENABLED=true`; sem essa flag, o desenvolvimento usa guest.
O overlay remove guest, habilita o provider OIDC e usa o resolver
`emailMatchingUserEntityProfileEmail`, com
`dangerouslyAllowSignInWithoutUserInCatalog: true`. O Keycloak é a fonte de
autenticação e autorização; uma entidade `User` no catálogo é opcional.
O provedor usa `prompt: auto`: com uma sessão Keycloak existente o SSO é
silencioso; sem sessão, o popup mostra o formulário de login em vez de falhar
com `login_required`.

O realm declara mappers explícitos para `preferred_username`, `email`,
`email_verified` e `groups`. O frontend inicia o fluxo com `openid` e o backend acrescenta
`profile email` (`additionalScopes`) para garantir que o perfil retornado pelo
provedor contenha o e-mail usado pelo resolver. Os mappers continuam sendo a
fonte explícita das claims; os client scopes são apenas suporte ao protocolo.
Entidades `User` fornecem ownership, grupos e navegação do catálogo, mas não são
pré-requisito para login. Quando usadas, são cadastradas por revisão no Git;
não há criação automática de entidades no catálogo.
Esta implementação não altera o realm de produção nem provisiona contas pessoais.

O certificado e a terminação TLS do hostname são responsabilidade do proxy do
servidor. O processo do Backstage continua ouvindo HTTP apenas na rede local;
`X-Forwarded-Proto` deve chegar como `https`.

A política allow-all oficial pressupõe que todos os usuários admitidos sejam membros
confiáveis de engenharia. Não é um modelo multitenant. Antes de admitir usuários com
privilégios distintos, restrinja permissões de scaffolder e publicação por Group.

## Autorização nas APIs exibidas no catálogo

O widget OpenAPI do portal reutiliza o access token OIDC mantido em memória pela
sessão atual do Backstage. Ao usar **Try it out**, ele acrescenta automaticamente
`Authorization: Bearer ...` às requisições destinadas aos hosts BookRush públicos ou
ao próprio host do portal. O token nunca é salvo em `localStorage`, colocado na URL,
gravado em documentação ou enviado para uma definição OpenAPI externa.

Essa integração apenas preenche a credencial da chamada no navegador; a autorização
continua sendo responsabilidade do microserviço. O serviço deve validar issuer,
assinatura, expiração, audience e permissões do token. Portanto, um `401` após o
preenchimento automático indica incompatibilidade de issuer/audience ou sessão
expirada, e um `403` indica falta de permissão. O portal não transforma a sessão
humana em credencial de serviço nem concede acesso a endpoints administrativos.

Para testar, entre no Backstage pelo Keycloak, abra uma entidade `API`, acesse a
definição OpenAPI e clique em **Try it out**. O interceptor só permite os hosts
`bookrush.jteodoro.tec.br`, o host atual e `localhost`/`127.0.0.1`; destinos externos
permanecem sem o token.

## GitHub

Leitura de repositório público não exige token. Privado: token fine-grained limitado
aos repositórios necessários, Contents read e Metadata read. Template via PR exige
Contents write e Pull requests write somente nos repositórios de destino. Não exige
Administration, organização inteira nem Actions secrets. Em escala prefira GitHub App
com installation access e mesmas permissões. Token de leitura do catálogo deve ser
separado de credencial de publicação quando houver múltiplos perfis de acesso.

PRs não são mergeados automaticamente. Não há credenciais em YAML, catálogo, template
ou logs. Não montar /var/run/docker.sock no portal. Documentos do Git privado e
artefatos TechDocs devem continuar privados; nunca tornar bucket público para resolver 403.
