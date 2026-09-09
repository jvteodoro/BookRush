# Operação

## Provisionamento antes do Jenkins

No host e no clone estável, configure .env e:
```bash
docker compose -f infrastructure/compose.yaml --env-file .env up -d --build --wait seaweedfs
```
Só então implantar o backend novo; ele cria buckets antes de ficar pronto.
Jenkins continua recriando só API/frontend. O script novo verifica saúde de
bookrush-seaweedfs-1 e recusa deploy se ausente. A pipeline executa o script de deploy do checkout selecionado; a pipeline remota usa
a branch publicada, nunca alterações locais. Não provisionamos o servidor
automaticamente durante testes.

## Backup consistente (manual)

Pausar ingestão/processadores, parar API para impedir novas escritas e esperar
uploads em andamento concluírem. Registrar versão das imagens e timestamp.
Fazer pg_dump -Fc da base e backup de roles separadamente; proteger o arquivo
porque metadata e credenciais de roles podem ser sensíveis. Parar SeaweedFS
e arquivar todo o volume seaweedfs_data, incluindo filer/master/volume.
Não copiar só os arquivos .dat: metadata do filer é necessária.

Exemplo do dump, executado no host com .env já configurado:
```bash
umask 077
mkdir -p backups
docker compose -f infrastructure/compose.yaml --env-file .env exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backups/catalog.dump
```
backups/ deve ficar fora do Git (está ignorado). Copiar backups para outro
disco/servidor e definir retenção. Um volume persistente não substitui backup.

Para snapshot físico, com SeaweedFS parado, montar o volume em container auxiliar
somente leitura e usar tar/cp -a preservando propriedade/modos. Descobrir o nome
com docker inspect; não assumir nome de volume em ambientes com outro projeto.
O script test-storage.sh contém execução completa equivalente, apenas em
volumes exclusivos de teste, incluindo restauração.

## Restore (manual)

Restaurar dump em PostgreSQL vazio compatível com pg_restore, restaurar snapshot
completo de SeaweedFS em volume novo e iniciar a mesma versão fixada. Conferir
inventário, HEAD e SHA-256 por GET antes de retomar escritas. Validar amostra de
URLs assinadas e ausência de órfãos/referências quebradas. Não sobrescrever
volumes originais antes de confirmar recuperação. O teste automatizado restaura
um dump em restore_probe e o snapshot SeaweedFS em volume descartável, e compara
bytes por URL assinada. Restore de produção não foi executado.

## Reconciliação e remoção

Consultar book_asset_version com status PENDING_UPLOAD/FAILED/MISSING e comparar
bucket/key com HEAD. PENDING antigo pode indicar queda durante upload ou commit.
AVAILABLE sem objeto deve virar MISSING após confirmar que não é falha de rede.
FAILED com objeto indica compensação pendente: verificar IDs/origem, remover
objeto e preservar histórico. Não tratar 403/timeout como objeto ausente.

Para órfãos, listar objetos e comparar com todas as versões (inclusive DELETED).
Nunca remover por idade sem confirmar que não existe upload ativo. Não há job
automático de reconciliação. DELETE administrativo é repetível: asset DELETED
bloqueia emissão de novos links antes da remoção física; retentar após falha.
Links já emitidos não têm revogação individual e podem durar até o TTL.

## Capacidade, retenção e credenciais

Monitorar espaço no volume Docker. O modo local usa arquivos de volume com
limite de 128 MB e até 32 volumes, adequado para desenvolvimento; não é limite
de quantidade de livros. Expansão real exige planejar novos volume servers e
replicação; documentar a topologia antes de migrar. Não configurar lifecycle
cego para source/public. Limpeza processing/ml exige conferir linhagem.

Rotação inicial: pausa breve, substituir o par de chaves no .env e recriar
SeaweedFS/backend, depois validar upload/GET. URLs antigas podem deixar de
funcionar. Token de API também requer recriação do backend. Não imprimir env,
headers ou URLs em logs. Imagem gera JSON de credenciais em /tmp com umask 077.

## Observabilidade

GET /actuator/metrics/bookrush.storage exige token administrativo; timer por
operation/result, sem IDs em tags. Logs têm bookId/assetId/versionId nas etapas
de coordenação e bucket/key/operação/resultado/duração no adaptador. Timer de GET
interno mede aquisição do stream, não todo o consumo. Falhas de compensação têm
log específico sem stack do SDK. Não habilitar wire logging do SDK nem access
logs com query string no proxy S3; assinaturas são credenciais temporárias.
