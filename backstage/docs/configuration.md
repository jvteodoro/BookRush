# Configuração por ambiente

app-config.yaml é a base sem segredos; app-config.docker.yaml adapta paths/DB/URLs
para Compose; app-config.local.yaml é ignorado e opcional. app-config.production.yaml
configura Git remoto e TechDocs externo; app-config.oidc.yaml habilita Keycloak.
Configuração carregada depois prevalece. Não inclua configurações locais na imagem.

| Variável | Uso | Obrigatória | Default / exemplo sem segredo |
|---|---|---|---|
| BACKSTAGE_POSTGRES_PASSWORD | Compose portal DB | Docker | nenhum; gere localmente |
| GITHUB_TOKEN | leitura privada / PRs | somente Git privado e template publish | nenhum |
| BACKSTAGE_PUBLIC_URL | URL usada pelo navegador para o portal, a descoberta do backend e o TechDocs | local: `http://localhost:17007`; produção: `https://docs-bookrush.jteodoro.tec.br` | `http://localhost:17007` |
| BACKSTAGE_CATALOG_URL | Location raiz no Git | produção | URL blob/main/catalog-info.yaml |
| POSTGRES_HOST | banco portal | produção | backstage-postgres em Docker |
| POSTGRES_USER | usuário portal | produção | backstage em Docker |
| POSTGRES_PASSWORD | senha banco portal | produção | nenhum |
| TECHDOCS_BUCKET | bucket dedicado | produção | bookrush-techdocs sugerido |

## Catálogo Graph e TechDocs

O `page:catalog-graph` possui raízes explícitas em `app-config.yaml`. O plugin
do Backstage inicia sem raízes quando essa configuração não existe e, nesse
caso, mostra um canvas vazio mesmo com entidades persistidas.

O ambiente com autenticação guest mantém as permissões do catálogo ativas, mas
desabilita a política de autenticação obrigatória do backend para os recursos
estáticos do TechDocs. O conteúdo HTML é carregado em um `iframe`; seus CSS e
JavaScript não conseguem enviar o bearer token das chamadas da aplicação. A
proteção de operações administrativas continua sendo responsabilidade das
permissões e dos serviços de domínio.
| AWS_REGION | assinatura S3 | produção | us-east-1 sugerido |
| TECHDOCS_S3_ENDPOINT | endpoint compatível S3 | produção | http://seaweedfs:8333 |
| TECHDOCS_ACCESS_KEY | leitura/publicação TechDocs | produção | nenhum |
| TECHDOCS_SECRET_KEY | segredo S3 | produção | nenhum |
| AUTH_OIDC_METADATA_URL | discovery Keycloak | OIDC | realm/.well-known/openid-configuration |
| AUTH_OIDC_CLIENT_ID | cliente confidencial portal | OIDC | bookrush-backstage |
| AUTH_OIDC_CLIENT_SECRET | secret Keycloak | OIDC | nenhum |
| AUTH_SESSION_SECRET | criptografia de sessão | OIDC | valor aleatório longo |
| CHROME_BIN | Chromium para render Mermaid | docs | /usr/bin/chromium na imagem |

Para ativar OIDC no Compose, defina `BACKSTAGE_OIDC_ENABLED=true` e preencha
as quatro variáveis `AUTH_*` acima. O script de entrada falha antes de iniciar
o backend se faltar secret ou session secret. Aplique o client/mappers antes de
recriar o portal:

```bash
bash infrastructure/keycloak/reconcile.sh --env-file .env
docker compose --project-name bookrush-portal \
  --file backstage/compose.yaml --env-file .env up -d --build --wait \
  backstage-postgres backstage
```

Para desenvolvimento local, mantenha `BACKSTAGE_OIDC_ENABLED=false`; guest
continua disponível apenas no processo local. O domínio público deve usar
HTTPS e o callback configurado no client Keycloak.

O catálogo readonly impede registro manual via API. Editar YAML no Git é o caminho
de atualização. Scaffolder ainda cria tasks e PRs; o merge e registro são revisados.

## Publicação HTTPS

O hostname oficial é `docs-bookrush.jteodoro.tec.br`. O DNS deve apontar para o
servidor que termina TLS. O Nginx encaminha esse virtual host para
`127.0.0.1:17007`, porta publicada pelo `backstage/compose.yaml`. Antes de
habilitar OIDC, emita o certificado para esse hostname e configure no Keycloak o
redirect URI indicado na documentação de segurança.

Os virtual hosts BookRush são modulares: instale todos com
`sudo bash infrastructure/nginx/install-bookrush-vhosts.sh`. O instalador faz
backup, remove blocos antigos dos domínios BookRush, instala os módulos em
`/etc/nginx/conf.d`, executa `nginx -t` e só então recarrega o serviço.
