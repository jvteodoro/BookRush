# Estudo de incidentes e correções do deploy

Este documento registra os problemas encontrados durante a implantação do
BookRush, as causas raiz, as correções aplicadas e as validações executadas.
Ele serve como material de estudo e como guia para diagnóstico de incidentes
sem remover volumes persistentes.

## 1. Como investigar um deploy que falhou

O deploy usa o projeto Compose `bookrush` e aguarda os healthchecks. Quando um
serviço falha, o primeiro passo é identificar estado, imagem e logs:

```bash
docker ps -a --filter name=bookrush-
docker inspect bookrush-catalog-service-1 \
  --format 'state={{.State.Status}} exit={{.State.ExitCode}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} image={{.Config.Image}}'
docker logs --tail=200 --timestamps bookrush-catalog-service-1
```

O script `infrastructure/jenkins/deploy-compose.sh` agora imprime esse
diagnóstico antes do rollback. O rollback é útil somente quando a imagem
anterior ainda consegue iniciar; migrations incompatíveis podem fazer a imagem
antiga falhar também.

## 2. `AssetService` sem construtor padrão

### Sintoma

Os testes de integração falhavam com:

```text
Failed to instantiate AssetService: No default constructor found
```

### Causa

`AssetService` tinha mais de um construtor e nenhum estava indicado como
construtor de injeção. O Spring não conseguia decidir qual usar.

### Solução

O construtor completo, que recebe `EntityManager`, transação, storage,
propriedades, buckets e `JdbcTemplate`, recebeu `@Autowired`. O construtor
reduzido foi mantido para testes unitários.

```java
@Autowired
public AssetService(EntityManager em, PlatformTransactionManager manager,
    ObjectStorage storage, StorageProperties props,
    BucketSelector buckets, JdbcTemplate jdbc) {
  // inicialização
}
```

Adicionar Lombok não era necessário. Uma anotação explícita deixa o contrato
de infraestrutura visível e evita introduzir uma dependência apenas para
gerar um construtor.

## 3. `StorageProperties.buckets()` nulo

### Sintoma

Com storage habilitado, o serviço encerrava com:

```text
Cannot invoke StorageProperties.Buckets.all() because buckets() is null
```

### Causa

O binding de configuração pode omitir o objeto aninhado `storage.buckets`
quando apenas endpoint ou credenciais são informados. O record de configuração
aceitava `null` e `StorageConfiguration` o usava durante a criação dos buckets.

### Solução

O construtor compacto de `StorageProperties` normaliza o objeto aninhado e
aplica os defaults `books-source`, `books-public`, `books-processing`,
`books-ml` e `books-raw`. Os nomes continuam externalizados por variáveis de
ambiente.

## 4. Migrations V7/V9 e dados legados

### Problemas encontrados

Os testes de upgrade revelaram três problemas distintos:

- V7 referenciava o schema literal `catalog`, quebrando upgrades em schemas
  temporários.
- V9 aplicava a regra de verificação antes de normalizar dados legados.
- Inserts e transições para `AVAILABLE` não distinguiam fixtures legadas de
  arquivos realmente verificados.

### Solução

As migrations passaram a:

- usar o schema atual durante a remoção de constraints;
- normalizar `availability_status` a partir de `status`;
- preencher `verified_at` para dados legados;
- validar hash, tamanho e MIME type em transições para `AVAILABLE`;
- retornar SQLSTATE de violação de check, traduzido pelo Spring como
  `DataIntegrityViolationException`;
- aplicar a compatibilidade adicional em `V12__storage_verification_compatibility.sql`.

Migrations já aplicadas não devem ser editadas em instalações reais. Quando a
base já possui V7/V9, a correção deve ser uma migration nova e o histórico deve
ser reparado uma única vez.

## 5. Checksum do Flyway na base persistente

### Sintoma

O catálogo não iniciava com:

```text
Migration checksum mismatch for migration version 7
Migration checksum mismatch for migration version 9
```

### Causa

V7 e V9 já estavam registradas em `catalog.flyway_schema_history`, mas os
arquivos resolvidos pela imagem tinham sido alterados posteriormente.

### Procedimento aplicado

1. Foi criado um dump antes da alteração:

   ```bash
   docker exec bookrush-postgres-1 pg_dump -U bookrush -d bookrush -Fc \
     > /tmp/bookrush-before-flyway-repair.dump
   ```

2. O Flyway foi executado na rede Docker real:

   ```bash
   set -a; . /run/bookrush.env; set +a
   docker run --rm --network bookrush_bookrush \
     -v /home/enoch/projects/LabSoft/BookRush/services/catalog-service/src/main/resources/db/migration:/flyway/sql:ro \
     -e FLYWAY_URL=jdbc:postgresql://bookrush-postgres-1:5432/bookrush \
     -e FLYWAY_USER=bookrush \
     -e FLYWAY_PASSWORD="$POSTGRES_PASSWORD" \
     -e FLYWAY_SCHEMAS=catalog \
     -e FLYWAY_DEFAULT_SCHEMA=catalog \
     -e FLYWAY_LOCATIONS=filesystem:/flyway/sql \
     flyway/flyway:10 repair
   ```

3. O catálogo foi recriado e o healthcheck confirmou a conexão com o banco.

O Compose expõe `FLYWAY_VALIDATE_ON_MIGRATE` para instalações legadas. Depois
de reparar uma instalação, prefira definir:

```env
FLYWAY_VALIDATE_ON_MIGRATE=true
```

Não remova o volume PostgreSQL para resolver checksum.

## 6. `LeaseCoordinator` sem construtor Spring

### Sintoma

Depois que o catálogo voltou, a ingestão falhou com:

```text
Failed to instantiate LeaseCoordinator: No default constructor found
```

### Causa e solução

A classe tinha os construtores `(JdbcTemplate)` e `(JdbcTemplate, Clock)`, mas
nenhum estava anotado. O construtor de produção recebeu `@Autowired`; o
construtor com `Clock` permaneceu disponível para testes determinísticos.

O container corrigido iniciou, conectou ao PostgreSQL e ficou `Healthy`.

## 7. Dependência DuckDB indisponível no Maven

### Sintoma

O build da ingestão falhava porque `org.duckdb:duckdb_jdbc:1.3.2` não existia
no Maven Central.

### Solução

Foi usada a versão publicada `1.3.2.0` no `pom.xml`. O build deve sempre ser
executado dentro do Docker Maven, como faz a pipeline; Maven não precisa estar
instalado no host.

## 8. Validação correta da pipeline

Validar somente `mvn package` não cobre os testes Failsafe. O fluxo usado para
validar o Jenkins foi:

```bash
docker build --target build -t bookrush/catalog-test:verify services/catalog-service
docker run --rm bookrush/catalog-test:verify mvn -B verify
bash scripts/test-storage.sh
```

`scripts/test-storage.sh` sobe PostgreSQL e SeaweedFS efêmeros e verifica:

- 40 testes de integração;
- migrations do zero e upgrade;
- upload, HEAD, download e delete S3;
- URL pré-assinada após recriação do SeaweedFS;
- backup lógico PostgreSQL;
- backup e restauração física do volume SeaweedFS.

O resultado validado foi `40 testes, 0 falhas, 0 erros` e `BUILD SUCCESS`.

## 9. Regras operacionais aprendidas

- Nunca remover volumes persistentes para corrigir falhas de aplicação.
- Ler a primeira causa `Caused by` do log; mensagens de contexto repetidas
  podem esconder a falha real.
- Toda classe Spring com múltiplos construtores deve ter um construtor de
  injeção explícito ou um único construtor público.
- Não alterar migrations aplicadas; criar uma migration aditiva e usar
  `flyway repair` somente após backup.
- A pipeline deve executar Failsafe e os testes de storage, não apenas o build
  do JAR.
- Depois de um rollback, verificar se a imagem antiga é compatível com o
  histórico de migrations atual.
- Registrar no Beads a causa, os comandos e a evidência de validação.

## 10. Estado final validado

Após as correções, os containers principais ficaram:

```text
catalog-service       healthy
book-ingestion-service healthy
frontend              healthy
reverse-proxy         healthy
postgres              healthy
seaweedfs             healthy
```

O smoke test do catálogo retornou:

```json
{"database":"up","status":"ok","service":"catalog-service"}
```
