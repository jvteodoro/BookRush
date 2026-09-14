# OIDC do Jenkins com Keycloak

O Jenkins usa o realm administrativo `bookrush-platform`. O login humano do
produto permanece no realm `bookrush`; não são a mesma fronteira de confiança.
O client `bookrush-jenkins` é confidencial e usa Authorization Code. Jobs e
credenciais de pipeline continuam usando identidades técnicas próprias.

## Configuração declarativa

O client e seus mappers estão em
`infrastructure/keycloak/bookrush-platform-realm.json`. O segredo não está no
Git: ele é lido do `.env` protegido pelo servidor.

```bash
bash infrastructure/keycloak/reconcile.sh --env-file .env
bash infrastructure/bootstrap/start-jenkins.sh --env-file .env --no-build
```

O client deve usar o redirect exato:

```text
https://jenkins-bookrush.jteodoro.tec.br/securityRealm/finishLogin
```

O realm declara mappers específicos do Jenkins:

| Mapper | Claim | Uso |
|---|---|---|
| `jenkins-preferred-username` | `preferred_username` | identificador da conta Jenkins |
| `jenkins-groups` | `groups` | entrada na Role Strategy |

O JCasC em `infrastructure/jenkins/casc/jenkins-oidc.yaml` usa
`preferred_username` como `userNameField` e `groups` como `groupsFieldName`.
O escopo solicitado é somente `openid`, porque `profile` e `email` não existem
como client scopes no realm atual.

## Variáveis de runtime

No `.env` do host, mantenha os valores abaixo sem versioná-los:

```dotenv
CASC_JENKINS_CONFIG=/opt/bookrush/casc/jenkins-oidc.yaml
JENKINS_OIDC_CLIENT_ID=bookrush-jenkins
JENKINS_OIDC_CLIENT_SECRET=<segredo-do-client>
JENKINS_OIDC_WELL_KNOWN_URL=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform/.well-known/openid-configuration
```

O secret é injetado somente no ambiente do container. Não deve aparecer em
logs, imagens, `docker compose config` expandido ou relatórios Jenkins.

## Grupos e autorização

| Grupo Keycloak | Role Jenkins |
|---|---|
| `platform-admins` | administração explícita |
| `platform-operators` | leitura, execução e cancelamento aprovados |
| `platform-developers` | leitura e build conforme concessão |

Pertencer ao realm do produto, ter o mesmo email ou possuir uma role com o
mesmo nome em outro realm não concede acesso ao Jenkins.

## Validação

Confira a configuração efetiva sem imprimir o secret:

```bash
docker exec bookrush-jenkins-1 sh -lc \
  'grep -n -A14 "<securityRealm" /var/jenkins_home/config.xml \
   | sed -E "s#<clientSecret>.*</clientSecret>#<clientSecret>REDACTED</clientSecret>#"'
```

Inicie uma prova humana sempre por uma autorização nova:

```text
https://jenkins-bookrush.jteodoro.tec.br/securityRealm/commenceLogin
```

O redirect deve apontar para o issuer `bookrush-platform`, client
`bookrush-jenkins`, `scope=openid` e o callback HTTPS acima.

## Diagnóstico de callback

O parâmetro `code` do callback é um authorization code de uso único e curta
validade. Atualizar a URL, voltar para uma aba antiga ou repetir uma requisição
que retornou erro faz o Keycloak registrar `invalid_code`/`Code not valid`.
O Jenkins não pode trocar esse código novamente; descarte a URL e inicie nova
autorização. Essa falha pode aparecer como usuário vazio porque o callback não
chegou a criar a identidade Jenkins.

```bash
docker logs --since 5m bookrush-jenkins-1 2>&1 \
  | grep -E 'Oidc|invalid_grant|Code not valid|username'
docker logs --since 5m bookrush-keycloak-1 2>&1 \
  | grep -E 'CODE_TO_TOKEN|invalid_code|bookrush-jenkins'
```

Nunca registre ou compartilhe a URL completa do callback: ela contém uma
credencial temporária. Se o erro persistir com uma autorização nova, valide o
mapper `preferred_username`, o client secret e o redirect exato.

## Ordem operacional

1. Preserve uma sessão break-glass controlada antes de alterar o realm.
2. Reconcilie o Keycloak.
3. Recrie apenas o container Jenkins para carregar o JCasC.
4. Valide discovery, redirect e login humano.
5. Confirme que jobs, credenciais e histórico permanecem no volume
   `jenkins_home`.

A reconciliação é incremental e não importa um realm vazio sobre uma instalação
populada nem remove usuários.
