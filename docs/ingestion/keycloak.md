# Autenticação com Keycloak

O endpoint administrativo do ingestion-service usa OIDC emitido pelo realm
`bookrush-platform` do Keycloak. Os papéis são `OPERATOR`, `REVIEWER` e
`CLEANUP`; o realm `bookrush` fica reservado ao produto.

Para desenvolvimento, suba o perfil de autenticação:

```bash
KEYCLOAK_ADMIN_PASSWORD=change-me \
docker compose --profile auth up -d keycloak
```

## Acesso por domínio público

O endereço público padrão é `keycloak-bookrush.jteodoro.tec.br`. Crie um
registro DNS apontando esse nome para o servidor e execute
`sudo bash infrastructure/nginx/install-bookrush-vhosts.sh` no checkout. O
módulo `infrastructure/nginx/conf.d/bookrush-keycloak.conf` é instalado com os
demais virtual hosts. Emita o certificado TLS para esse nome. O Nginx encaminha para o Traefik em
`127.0.0.1:18081`, que encaminha o host para o container Keycloak.

Defina no `.env` do servidor a URL externa completa antes de iniciar o
container:

```dotenv
KEYCLOAK_PUBLIC_URL=https://keycloak-bookrush.jteodoro.tec.br
INGESTION_OIDC_ISSUER=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform
```

Depois, abra `https://keycloak-bookrush.jteodoro.tec.br/admin/`. O console
administrativo usa o realm `master`; operadores usam o realm
`bookrush-platform` e usuários do produto usam `bookrush`. Para instalar em outro ambiente, substitua o hostname no arquivo
dinâmico do Traefik, no virtual host Nginx e nessas duas variáveis.

Não publique a porta `18088` diretamente na Internet. O container continua
escutando nessa porta apenas para administração local e o acesso externo passa
por HTTPS no Nginx.

Em uma instalação local sem domínio, configure o serviço com o issuer interno:

```bash
INGESTION_SECURITY_ENABLED=true
INGESTION_OIDC_ISSUER=http://keycloak:8080/realms/bookrush-platform
INGESTION_OIDC_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs
INGESTION_OIDC_AUDIENCE=bookrush-ingestion
```

Na instalação pública, use o issuer HTTPS mostrado acima. O URI de chaves pode
continuar interno, pois o ingestion-service e o Keycloak compartilham a rede
Compose:

```dotenv
INGESTION_OIDC_ISSUER=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform
INGESTION_OIDC_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs
INGESTION_OIDC_AUDIENCE=bookrush-ingestion
```

Uma fixture `operator` histórica pode existir em volumes antigos, mas não é
mais versionada no realm de produto e não recebe acesso automático ao endpoint
administrativo no realm `bookrush-platform`. Para um ensaio local, crie um operador temporário no
console/API administrativa do realm de plataforma, habilite MFA quando
necessário e remova-o ao terminar. O produto não usa password grant:

```bash
docker exec bookrush-keycloak-1 /opt/keycloak/bin/kcadm.sh get users \
  -r bookrush-platform --fields username,enabled
```

Nenhuma senha real deve ser commitada.
Em produção, o segredo do administrador e credenciais de cliente devem vir do
secret manager ou do ambiente protegido do Jenkins.

## Leitura de catálogo pelo cliente web

O cliente público `bookrush-web` usa o realm `bookrush`. As thumbnails protegidas
exigem a authority `SCOPE_bookrush.catalog.read`; por isso o client scope
`bookrush.catalog.read` deve existir no realm e estar associado ao cliente web.
Depois de alterar essa configuração, o usuário precisa renovar a sessão para
receber um token novo com a claim `bookrush.catalog.read`. Tokens emitidos antes
da alteração continuam retornando HTTP 403.

A definição do scope fica em `infrastructure/keycloak/bookrush-realm.json`. Em uma
instalação persistente já existente, execute o reconcile do Keycloak ou associe o
scope ao cliente `bookrush-web` pelo console administrativo; não altere a regra
de segurança do catálogo para ignorar a autorização.
