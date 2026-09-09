# BookRush

Base de monorepo para microsserviços Java e frontend React, executável tanto
localmente com Docker Compose quanto em Kubernetes.

## Estrutura

- `services/`: microsserviços Java independentes.
- `frontend/`: aplicação React entregue pelo Nginx.
- `infrastructure/compose.yaml`: ambiente local completo (PostgreSQL, Redis,
  backend, frontend e Jenkins).
- `infrastructure/kubernetes/`: manifests Kubernetes gerenciados por Kustomize.
- `Jenkinsfile`: pipeline de teste, build, publicação e deploy.

## Desenvolvimento local

1. Crie a configuração local:

   ```bash
   cp .env.example .env
   ```

2. Suba os serviços:

   ```bash
   docker compose -f infrastructure/compose.yaml --env-file .env --profile ci up --build
   ```

O gateway fica restrito a `http://127.0.0.1:18081`; o frontend fica em
`http://127.0.0.1:18080` para desenvolvimento direto, o Jenkins em
`http://127.0.0.1:18082`, o pgAdmin em `http://127.0.0.1:18083` e o serviço de
ingestão em `http://127.0.0.1:18090`. PostgreSQL, Redis e o catálogo não são
publicados diretamente no host.
Para expor a aplicação, adicione o modelo em
`infrastructure/nginx/bookrush.conf.example` ao Nginx do servidor, alterando o
domínio e, quando aplicável, a configuração TLS.

Com o modelo Nginx aplicado, todas as requisições seguem para o gateway
containerizado. Ele atende o frontend em `/`, o catálogo em `/api/...` e a
ingestão em `/ingestion/api/...`; o prefixo `/ingestion/` é removido pelo
gateway antes de encaminhar ao serviço interno.

As interfaces OpenAPI são geradas automaticamente pelo Springdoc: catálogo em
`/swagger-ui.html` (especificação `/v3/api-docs`) e ingestão em
`/ingestion-docs/swagger-ui.html` (especificação `/ingestion-docs/v3/api-docs`).
As rotas de documentação são públicas para consulta; endpoints administrativos
continuam protegidos pela segurança de cada serviço.

Antes de subir o Jenkins em Linux, configure `DOCKER_GID` no `.env` com o grupo
do socket Docker: `stat -c '%g' /var/run/docker.sock`.

## Administração do banco

O pgAdmin é publicado pelo Nginx exclusivamente em
`admin.bookrush.jteodoro.tec.br`. Copie
`infrastructure/nginx/bookrush-admin.conf.example` para a configuração do
Nginx e crie antes o usuário da camada adicional de Basic Auth:

```bash
sudo htpasswd -c /etc/nginx/.htpasswd-bookrush-admin seu-usuario-admin
sudo nginx -t && sudo systemctl reload nginx
```

Proteja também o subdomínio com uma Application no Cloudflare Access e use
TLS no origin (modo **Full (strict)**). No pgAdmin, entre com as credenciais
`PGADMIN_*` do `.env` e registre o servidor com host `postgres`, porta `5432`
e as credenciais `POSTGRES_*` do mesmo arquivo.

## Kubernetes

Copie o secret de exemplo e substitua os valores antes do deploy:

```bash
cp infrastructure/kubernetes/base/db-secret.example.yaml infrastructure/kubernetes/base/db-secret.yaml
kubectl apply -k infrastructure/kubernetes/base
```

As imagens padrão são exemplos. O Jenkins substitui as imagens pelos artefatos
publicados. Veja `infrastructure/kubernetes/README.md` para configurar o
registro e o acesso ao cluster.

## Teste de conexão da aplicação

Suba a aplicação e aguarde os checks de saúde (Jenkins é opcional):

```bash
docker compose -f infrastructure/compose.yaml --env-file .env up -d --build --wait
```

Abra `http://127.0.0.1:18080` no servidor ou o domínio configurado no proxy
reverso. A tela React consulta `GET /api/status`, e a API Java executa
`SELECT 1` no PostgreSQL. O botão **Testar conexão novamente** repete a
verificação. A API retorna HTTP 503 quando não consegue consultar o banco;
a tela informa a falha, sem confirmar uma conexão inexistente.

```bash
curl --fail http://127.0.0.1:18080/api/status
docker compose -f infrastructure/compose.yaml --env-file .env ps
docker compose -f infrastructure/compose.yaml --env-file .env logs --tail=100 catalog-service frontend
```

Resposta esperada: `{"service":"catalog-service","status":"ok","database":"up"}`
(a ordem dos campos pode variar). Redis e pgAdmin sobem junto; esse teste
verifica apenas React, proxy, Java e PostgreSQL. As credenciais locais ficam
no `.env`, ignorado pelo Git. Os testes da API rodam durante o build Java.
Para desenvolver com Vite fora do Docker, `/api` é encaminhado para uma
API Java em `localhost:8080`.

## Deploy pelo Jenkins

O painel usa `https://jenkins-bookrush.jteodoro.tec.br`. O job `bookrush-deploy`
permite informar a branch e executar build/deploy no Compose deste servidor,
com tentativa de restauração das imagens anteriores em caso de falha.
Veja [instalação e uso do Jenkins](infrastructure/jenkins/README.md).

## Agentes de IA e Beads

O fluxo de desenvolvimento dos agentes está em [AGENTS.md](AGENTS.md).
Use Beads (`bd`) para tarefas e dependências; veja [configuração e uso](docs/beads.md).

## Schema PostgreSQL

O catálogo usa migrations Flyway no schema `catalog`, com validação Hibernate.
Veja [documentação do banco](docs/database/README.md) e execute os testes isolados
com `bash scripts/test-database.sh`.

## Arquivos dos livros

Os arquivos ficam no object storage SeaweedFS, acessado por API S3; PostgreSQL
armazena referências e metadados. Configure as variáveis STORAGE_* e
ASSET_ADMIN_TOKEN do .env.example e suba o Compose normalmente. Veja
[documentação de storage](docs/storage/README.md); a suíte completa é
`bash scripts/test-storage.sh`. Todos os buckets começam privados.
