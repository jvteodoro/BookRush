# Autenticação com Keycloak

O endpoint administrativo do ingestion-service usa OIDC emitido pelo realm
`bookrush` do Keycloak. Os papéis são `OPERATOR`, `REVIEWER` e `CLEANUP`.

Para desenvolvimento, suba o perfil de autenticação:

```bash
KEYCLOAK_ADMIN_PASSWORD=change-me \
docker compose --profile auth up -d keycloak
```

## Acesso por domínio público

O endereço público padrão é `keycloak-bookrush.jteodoro.tec.br`. Crie um
registro DNS apontando esse nome para o servidor e instale o virtual host
[`infrastructure/nginx/bookrush-keycloak.conf.example`](../../infrastructure/nginx/bookrush-keycloak.conf.example)
no Nginx do host. Emita o certificado TLS para esse nome e habilite as
diretivas HTTPS do exemplo. O Nginx encaminha para o Traefik em
`127.0.0.1:18081`, que encaminha o host para o container Keycloak.

Defina no `.env` do servidor a URL externa completa antes de iniciar o
container:

```dotenv
KEYCLOAK_PUBLIC_URL=https://keycloak-bookrush.jteodoro.tec.br
INGESTION_OIDC_ISSUER=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush
```

Depois, abra `https://keycloak-bookrush.jteodoro.tec.br/admin/`. O console
administrativo usa o realm `master`; usuários da aplicação usam o realm
`bookrush`. Para instalar em outro ambiente, substitua o hostname no arquivo
dinâmico do Traefik, no virtual host Nginx e nessas duas variáveis.

Não publique a porta `18088` diretamente na Internet. O container continua
escutando nessa porta apenas para administração local e o acesso externo passa
por HTTPS no Nginx.

Em uma instalação local sem domínio, configure o serviço com o issuer interno:

```bash
INGESTION_SECURITY_ENABLED=true
INGESTION_OIDC_ISSUER=http://keycloak:8080/realms/bookrush
INGESTION_OIDC_JWK_SET_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs
INGESTION_OIDC_AUDIENCE=bookrush-ingestion
```

Na instalação pública, use o issuer HTTPS mostrado acima. O URI de chaves pode
continuar interno, pois o ingestion-service e o Keycloak compartilham a rede
Compose:

```dotenv
INGESTION_OIDC_ISSUER=https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush
INGESTION_OIDC_JWK_SET_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs
INGESTION_OIDC_AUDIENCE=bookrush-ingestion
```

O usuário de fixture `operator` existe apenas para desenvolvimento e deve
trocar a senha no primeiro login. Em imagens importadas, confirme a credencial
com o comando administrativo abaixo antes do teste de password grant:

```bash
docker exec bookrush-keycloak-1 \
  /opt/keycloak/bin/kcadm.sh set-password -r bookrush \
  --username operator --new-password "$BOOKRUSH_FIXTURE_PASSWORD" \
  --temporary=false
```

Nenhuma senha real deve ser commitada.
Em produção, o segredo do administrador e credenciais de cliente devem vir do
secret manager ou do ambiente protegido do Jenkins.
