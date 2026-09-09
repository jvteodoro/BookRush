# Relatório — épico bookrush-0k2

## Entrega

SeaweedFS 4.46 OSS com persistência em volume Docker; PostgreSQL continua no
volume nomeado original. AWS SDK Java v2 2.31.54, configuração validada, quatro
buckets configuráveis e privados, interface independente do fornecedor.
Assets/versões usam as migrations/JPA do épico de schema, sem duplicar modelos.

Upload administrativo calcula SHA-256, valida conteúdo, reserva metadata,
faz PUT condicional/HEAD e compensa falhas. Download retorna capability temporária
após autorização; não retransmite livro no Spring. Aprovação explícita controla
PUBLIC. Remoção é repetível e mantém histórico. Token Bearer inicial substituível
futuramente por identidade federada; não há usuário/senha padrão de Spring.

## Arquivos criados

- infrastructure/seaweedfs/Dockerfile e start.sh: imagem fixada, configuração de
  credenciais em runtime e filer persistido.
- infrastructure/compose.storage-test.yaml: PostgreSQL/SeaweedFS reais isolados.
- scripts/test-storage.sh: build, suíte, GET externo, recriação e backup/restore.
- scripts/test-compose.sh: smoke da stack completa com volumes/portas de teste.
- catalog/storage: StorageProperties, StorageConfiguration, BucketSelector,
  ObjectKeyBuilder, ObjectStorage, StorageFailure, S3ObjectStorage.
- catalog/asset: UploadFile, AssetService, AssetSecurity, AssetController,
  DistributionController, AssetErrors.
- testes storage: StorageUnitTest, S3AdapterTest, AssetCompensationTest,
  StorageIT, AssetRecoveryIT.
- docs/storage: README, architecture, local-development, buckets,
  object-key-convention, data-model, operations, troubleshooting,
  migration-to-cloud, validation e report.
- ADR-002: escolha S3/SeaweedFS e alternativas.

## Arquivos alterados

Compose principal: SeaweedFS, volume e dependência/configuração do backend.
pom.xml: SDK v2, validação e Security. application.yml: propriedades e limite de
multipart, métricas. StatusControllerTest mantém teste MVC independente de S3.
Jenkinsfile e cópia de bootstrap: etapa S3 antes de publicar/deploy.
deploy-compose.sh: requisito de infraestrutura saudável.
.env.example, .gitignore, README raiz, docs de Jenkins/database: configuração,
backups ignorados e documentação consistente.

O .env local recebeu somente variáveis ausentes de storage, com credenciais
geradas localmente e não exibidas/commitadas. Nenhum serviço em uso foi recriado.

## Validação

Ver [evidências completas](validation.md). Foram executados Maven/build,
PostgreSQL e SeaweedFS reais, 50 testes, oito queries de schema, signed GET na
rede do host, privacidade, conditional PUT, compensação, retry e restauração.
Smoke completo retornou database=up através do frontend.

## Limitações deliberadas

Sem PUT assinado direto (faltaria finalização segura), CDN ou cloud deployment.
AUDIO/OTHER não têm detector e são rejeitados; validação de assinatura não é
antivírus. Reconciliação/limpeza/retenção de produção são manuais. Há um único
token de operador e uma credencial gateway de desenvolvimento. Produção
distribuída/OIDC/políticas por serviço exigem etapa própria.

Bootstrap cria buckets ausentes mas não apaga policies preexistentes. Licenças
iniciais mantêm permissões desconhecidas; aprovação não determina direitos
jurídicos. Não há scheduler/crawler/modelos ML. URLs emitidas podem valer até
o TTL. Backup de produção, proxy HTTPS externo e cloud não foram executados;
procedimentos para validação estão em operations/local-development/migration.

Código permanece local: sem commit, push, sync remoto de beads ou deploy.
Para Jenkins usar a entrega, publicar a branch, provisionar storage e usar a pipeline
atualizada conforme operations.md.

## Executar

```bash
cp .env.example .env
# Substitua as credenciais de exemplo no .env.
docker compose -f infrastructure/compose.yaml --env-file .env up -d --build
bash scripts/test-storage.sh
```
