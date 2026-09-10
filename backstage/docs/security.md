# Segurança e integração Git

Guest é somente para loopback/desenvolvimento. Nunca ative
`dangerouslyAllowOutsideDevelopment` em produção. No perfil público use Keycloak OIDC,
TLS no proxy, sessão secreta e catálogo User com email correspondente. Ausência de
usuário deve negar login; não permitir resolver que crie identidade irrestrita.

## Keycloak

Crie cliente confidencial bookrush-backstage no realm existente, authorization code,
redirect URI `https://docs-bookrush.jteodoro.tec.br/api/auth/oidc/handler/frame` e Web Origin exata.
Não habilite password grant. Configure as AUTH_* externamente e carregue a overlay
app-config.oidc.yaml após production. Configure email verificado e User no Git.
Esta implementação não altera o realm de produção nem provisiona contas pessoais.

O certificado e a terminação TLS do hostname são responsabilidade do proxy do
servidor. O processo do Backstage continua ouvindo HTTP apenas na rede local;
`X-Forwarded-Proto` deve chegar como `https`.

A política allow-all oficial pressupõe que todos os usuários admitidos sejam membros
confiáveis de engenharia. Não é um modelo multitenant. Antes de admitir usuários com
privilégios distintos, restrinja permissões de scaffolder e publicação por Group.

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
