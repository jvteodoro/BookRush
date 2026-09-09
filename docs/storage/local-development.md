# Desenvolvimento local

Pré-requisitos: Docker Engine, Compose v2, Bash. Build inicial baixa imagens e
dependências Maven; testes usam apenas PostgreSQL e SeaweedFS locais.

Da raiz do clone:

```bash
cp .env.example .env
# Troque STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY e ASSET_ADMIN_TOKEN no .env.
docker compose -f infrastructure/compose.yaml --env-file .env up -d --build
```

Credenciais de exemplo são somente de desenvolvimento. Gateway aceita letras,
dígitos, '_' e '-' nas duas chaves (geração hex é adequada). Nunca commitá-las.
POSTGRES continua no volume Docker postgres_data; SeaweedFS usa seaweedfs_data
para master, volumes e metadata filer LevelDB. Recriar containers preserva dados.

| Variável | Padrão / contrato |
|---|---|
| STORAGE_ENABLED | false fora do Compose; true no Compose |
| STORAGE_ENDPOINT | http://seaweedfs:8333 no backend Docker |
| STORAGE_PUBLIC_ENDPOINT | http://localhost:18333; trocar por HTTPS alcançável pelo leitor em servidor remoto |
| STORAGE_REGION | us-east-1 |
| STORAGE_ACCESS_KEY / STORAGE_SECRET_KEY | par; ausente no Spring usa cadeia de credenciais AWS |
| STORAGE_PATH_STYLE | true; S3 virtual-host pode usar false |
| STORAGE_URL_TTL_SECONDS | 300; intervalo 1–3600 |
| STORAGE_TIMEOUT | 30s; positivo até 5min |
| STORAGE_MAX_UPLOAD_BYTES | 104857600; máximo 100 MiB |
| ASSET_ADMIN_TOKEN | obrigatório ao habilitar storage; mínimo 32 caracteres |
| STORAGE_BUCKET_SOURCE/PUBLIC/PROCESSING/ML | books-source/books-public/books-processing/books-ml |
| BOOKRUSH_S3_PORT | 18333 no loopback |

Spring usa @ConfigurationProperties validado. SDK 2.31.54, SeaweedFS 4.46 fixados.
Checksum SHA-256 explícito no PUT; não usamos ETag como hash.
Clientes e presigner possuem região/path-style iguais, endpoints separados.
Storage habilitado com endpoint custom exige public-endpoint explícito.

## Saúde e buckets

```bash
docker compose -f infrastructure/compose.yaml --env-file .env ps
docker compose -f infrastructure/compose.yaml --env-file .env logs --tail 50 seaweedfs
```

O backend inicializa quatro buckets idempotentemente. Para listar manualmente
com AWS CLI instalado, forneça credenciais de desenvolvimento por ambiente:

```bash
AWS_ACCESS_KEY_ID=bookrush-dev AWS_SECRET_ACCESS_KEY=bookrush-development-secret-change-me \
  aws --endpoint-url http://localhost:18333 --region us-east-1 s3api list-buckets
```

Não mudar secrets apenas no .env sem recriar gateway/backend: processos precisam
receber a nova configuração. Não inserir usuário anônimo na configuração S3.

## API de exemplo

A obra deve existir no schema. Use pgAdmin para criar uma obra de desenvolvimento
com UUID interno e status ACTIVE. Source GUTENBERG já existe com UUID
10000000-0000-4000-8000-000000000001. Não há CRUD de obras nesta entrega.

Exemplo com token de desenvolvimento (substitua BOOK_ID):

```bash
printf 'Example book text\n' > /tmp/bookrush-example.txt
curl -f -H 'Authorization: Bearer bookrush-development-admin-token-change-me' \
  -F sourceId=10000000-0000-4000-8000-000000000001 -F type=TXT -F role=SOURCE \
  -F 'file=@/tmp/bookrush-example.txt;type=text/plain' \
  http://localhost:18080/api/admin/books/BOOK_ID/assets
```

Endpoints administrativos sob /api/admin/books/{bookId}/assets:
POST multipart, GET paginado (page, tamanho 50), GET /{assetId},
GET /{assetId}/versions, POST /{assetId}/versions (multipart),
POST /{assetId}/approve, GET /{assetId}/download-url e DELETE /{assetId}.
Upload retorna 201 com asset e versão; approve/delete 204; sem token 401,
papel insuficiente 403, vínculo inexistente 404, conflito/aprovação inválida 409,
arquivo inválido 400, limite multipart 413, indisponibilidade 503.
Versões mantêm tipo/role do asset; SOURCE não recebe novas versões.

Distribuição: GET /api/books/{bookId}/assets/{assetId}/download-url retorna
{url, expiresAt}, Cache-Control no-store. Abra a URL no navegador para download
direto. Não armazenar resposta em cache nem registrá-la em logs.
PUBLIC novo fica INACTIVE; definir licença aplicável (asset/edição) e aprovar
explicitamente após revisão. As licenças iniciais não concedem redistribuição.

## Testes e limpeza

```bash
bash scripts/test-storage.sh
# Smoke do Compose completo com portas dinâmicas, volumes de teste e .env.example:
bash scripts/test-compose.sh
# Somente banco, sem habilitar storage:
bash scripts/test-database.sh
```

O primeiro comando testa banco/SDK/API e recria SeaweedFS, verifica GET assinado
na rede do host e restaura backups em recursos descartáveis. Funciona com socket
Docker do Jenkins usando docker cp; nenhum bind do workspace para daemon remoto.
Não usa dados da aplicação. O segundo pode ignorar a classe StorageIT, por design.

Para parar desenvolvimento preservando dados:
```bash
docker compose -f infrastructure/compose.yaml --env-file .env down
```
down -v remove volumes da aplicação: só usar ao deliberadamente descartar todos
os dados de desenvolvimento. Não é comando de limpeza normal ou de deploy.

## Evolução distribuída

O entrypoint usa weed server (master/volume/filer/S3 no mesmo container).
filer.toml fixa LevelDB em /data/filer. Para produção distribuída, separar
processos, planejar metadata filer compartilhada, replicação/quórum, capacidade,
backups e endpoints internos. Não basta aumentar replicas deste serviço único.
