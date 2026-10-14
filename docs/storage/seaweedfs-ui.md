# Interface administrativa do SeaweedFS

O Filer do SeaweedFS é uma interface administrativa e não deve ser publicado
diretamente na internet. O serviço continua acessível somente na rede Docker em
`http://seaweedfs:8888`. A API S3 possui um hostname separado para URLs
assinadas: `https://seaweedfs-s3-bookrush.jteodoro.tec.br`.

## Acesso protegido

A interface será acessada por:

```text
https://seaweedfs-bookrush.jteodoro.tec.br
```

O domínio passa pelo Nginx e pelo Traefik. O serviço `seaweedfs-oidc` usa o cliente
`bookrush-seaweedfs` no realm `bookrush-platform` e encaminha o Filer somente depois
de um login OIDC válido no Keycloak. O acesso é restrito aos grupos Keycloak
`operators` e `platform-admins`; pertencer ao realm ou possuir uma conta de produto
não concede acesso.

O cliente possui um audience mapper próprio (`bookrush-seaweedfs`). Isso é
necessário porque o `oauth2-proxy` valida o audience do access token durante o
callback; apenas `azp` não é suficiente para criar a sessão.

O cliente, redirect URI e mappers de grupos estão versionados em
`infrastructure/keycloak/bookrush-platform-realm.json`. O segredo do cliente e o
segredo de cookie ficam exclusivamente no `.env` protegido:

```text
SEAWEEDFS_OIDC_CLIENT_SECRET
SEAWEEDFS_OAUTH2_COOKIE_SECRET
```

O endpoint de autorização (`SEAWEEDFS_OIDC_LOGIN_URL`) precisa usar o domínio
HTTPS público do Keycloak, pois esse URL é seguido pelo navegador. Os endpoints
de token, perfil e JWKS podem continuar usando `http://keycloak:8080` na rede
privada do Compose.

Depois de alterar o realm, execute:

```bash
bash infrastructure/keycloak/reconcile.sh --env-file .env
docker compose -f infrastructure/compose.yaml --env-file .env --profile auth up -d --wait seaweedfs seaweedfs-oidc reverse-proxy
```

## DNS e TLS

O DNS deve apontar `seaweedfs-bookrush.jteodoro.tec.br` e
`seaweedfs-s3-bookrush.jteodoro.tec.br` para o host do Nginx. O
registro wildcard `*.jteodoro.tec.br` cobre esse hostname porque ele possui
apenas um nível de subdomínio. É necessário um certificado que cubra
`seaweedfs-bookrush.jteodoro.tec.br` e `seaweedfs-s3-bookrush.jteodoro.tec.br` (ou
o wildcard `*.jteodoro.tec.br`). Os módulos versionados estão em
`infrastructure/nginx/conf.d/bookrush-seaweedfs*.conf` incluem o vhost HTTP para
ACME e o vhost HTTPS do S3. O S3 não deve ser colocado atrás do `oauth2-proxy`: a assinatura
presign é a autorização do objeto.

Depois de instalar os módulos, emita/renove um certificado cobrindo os dois
hostnames e defina no `.env`:

```text
STORAGE_PUBLIC_ENDPOINT=https://seaweedfs-s3-bookrush.jteodoro.tec.br
STORAGE_ALLOWED_ORIGINS=https://bookrush.jteodoro.tec.br
```

O deploy recusa `localhost`, HTTP ou endpoint ausente para impedir a geração de
URLs assinadas inutilizáveis pelo navegador.
O certificado do S3 fica em
`/etc/letsencrypt/live/seaweedfs-s3-bookrush.jteodoro.tec.br/`; a renovação deve
recarregar o Nginx após atualizar os arquivos. O bloco HTTPS versionado usa esses
caminhos para que o vhost continue reproduzível no host de produção.

Instalação no host:

```bash
sudo bash infrastructure/nginx/install-bookrush-vhosts.sh
sudo nginx -t
sudo systemctl reload nginx
```

Para wildcard, use o plugin DNS do provedor do domínio. O desafio HTTP não valida
um certificado `*.jteodoro.tec.br`.

O wildcard Nginx não expõe serviços arbitrários. Cada serviço futuro precisa de uma
rota explícita no arquivo `infrastructure/traefik/dynamic.yaml`, com autenticação e
permissões próprias.

## Desenvolvimento local

Para inspeção local sem publicar o Filer, use um override temporário que mapeie as
portas 8888 e 9333 somente para `127.0.0.1`. Não adicione essas portas ao Nginx
público nem remova a autenticação OIDC do proxy.
