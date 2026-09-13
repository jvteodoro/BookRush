# Bootstrap Ubuntu

O host precisa apenas de um clone do repositório e de um `.env` privado. Java,
Maven, Node, Python de documentação e as dependências dos microsserviços são
obtidos pelos Dockerfiles; não há instalação manual dessas ferramentas.

## Instalação

No Ubuntu 22.04 ou 24.04:

```bash
git clone https://github.com/jvteodoro/BookRush.git
cd BookRush
cp /caminho/seguro/bookrush.env .env
chmod 600 .env
sudo bash infrastructure/bootstrap/install-ubuntu.sh
```

O instalador instala Docker Engine + Compose v2, Git, Nginx, Certbot,
OpenSSL, `jq`, Python e ferramentas auxiliares. O usuário que executa o script
é adicionado ao grupo `docker`; abra uma nova sessão após a instalação.
O instalador também garante o plugin Buildx, necessário para os Dockerfiles.
Se aparecer `permission denied` no socket, encerre e abra a sessão novamente
ou execute com um usuário que pertença ao grupo `docker`.

## Configuração e subida

```bash
bash infrastructure/bootstrap/configure-ubuntu.sh
```

O script valida os dois Compose, calcula o caminho absoluto do clone para os
mounts do daemon Docker, deriva apenas `BACKSTAGE_POSTGRES_PASSWORD` a partir
de `POSTGRES_PASSWORD` em um arquivo temporário e inicia PostgreSQL, Redis,
SeaweedFS, Keycloak, catálogo, ingestão, frontend, Jenkins e Backstage.

```bash
bash infrastructure/bootstrap/configure-ubuntu.sh --no-up
bash infrastructure/bootstrap/configure-ubuntu.sh --without-ci --without-auth
bash infrastructure/bootstrap/configure-ubuntu.sh --no-build
```

O `.env` nunca é copiado para uma imagem nem versionado. Os dados ficam nos
volumes Docker nomeados `postgres_data`, `seaweedfs_data`, `redis_data`,
`pgadmin_data`, `keycloak_data`, `jenkins_home`, `backstage_postgres_data` e
`backstage_techdocs`. Não use `docker compose down --volumes` em uma instalação
que contenha dados.

O ciclo de vida também possui comandos dedicados:

```bash
# Iniciar/recriar somente o Jenkins e aguardar o endpoint /login
bash infrastructure/bootstrap/start-jenkins.sh

# Iniciar toda a plataforma, incluindo Jenkins, Keycloak e Backstage
bash infrastructure/bootstrap/start-environment.sh
```

`start-environment.sh` inicia o Compose principal com os perfis de autenticação,
aguarda os healthchecks, sobe o portal em seu Compose próprio e por último
chama `start-jenkins.sh`. Ambos aceitam `--no-build`, `--env-file FILE` e as
opções para omitir CI, autenticação ou portal. O Jenkins possui healthcheck
próprio para que `docker compose --wait` não retorne antes de estar acessível.

## Nginx e HTTPS

Depois da configuração, instale os virtual hosts no Nginx do host:

```bash
sudo bash infrastructure/nginx/install-bookrush-vhosts.sh
sudo certbot --nginx --redirect \
  -d bookrush.jteodoro.tec.br \
  -d jenkins-bookrush.jteodoro.tec.br \
  -d admin-bookrush.jteodoro.tec.br \
  -d keycloak-bookrush.jteodoro.tec.br \
  -d docs-bookrush.jteodoro.tec.br
```

DNS e certificados continuam sendo responsabilidade do operador; remova os
domínios que não apontarem para o host.
