# Interface administrativa do SeaweedFS

O Filer do SeaweedFS é uma interface administrativa e não deve ser publicado
diretamente na internet. O serviço continua acessível somente na rede Docker em
`http://seaweedfs:8888`; a API S3 continua privada e publicada no host apenas no
loopback.

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

Depois de alterar o realm, execute:

```bash
bash infrastructure/keycloak/reconcile.sh --env-file .env
docker compose -f infrastructure/compose.yaml --env-file .env --profile auth up -d --wait seaweedfs seaweedfs-oidc reverse-proxy
```

## DNS e TLS

O DNS deve apontar `seaweedfs-bookrush.jteodoro.tec.br` para o host do Nginx. O
registro wildcard `*.jteodoro.tec.br` cobre esse hostname porque ele possui
apenas um nível de subdomínio. É necessário um certificado que cubra
`seaweedfs-bookrush.jteodoro.tec.br` (ou o wildcard `*.jteodoro.tec.br`). O módulo versionado está em
`infrastructure/nginx/conf.d/bookrush-seaweedfs.conf` e contém somente o bootstrap HTTP.
O Certbot cria e gerencia o bloco HTTPS e os caminhos dos certificados; não adicione
diretivas SSL ao arquivo versionado.

Instalação no host:

```bash
sudo bash infrastructure/nginx/install-bookrush-vhosts.sh
sudo nginx -t
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
