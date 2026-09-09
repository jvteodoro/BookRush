# Banco do catálogo

Implementação incremental dos beads `bookrush-br1`. O schema `catalog` pertence
ao catalog-service. Flyway controla DDL; Hibernate valida após a migration.
Não há binários ou URLs assinadas no banco.

- [Schema implementado](schema.md)
- [Campos e regras das entidades](entities.md)
- [Índices](indexes.md)
- [Consultas SQL](queries.sql)
- [Validações executadas e limites](validation.md)
- [Decisões e modelo alvo](../adr/ADR-001-book-storage-data-model.md)
- [Fronteira de storage e inventário](../storage/architecture.md)

## Executar migrations

Ao iniciar o backend, Spring Boot executa Flyway antes de inicializar JPA.
Use o Compose existente conforme o README raiz. Esse comando em um ambiente
existente aplica migrations no banco configurado: revise-as e faça backup antes
de um deploy. Não use Hibernate create/update ou baseline-on-migrate para
contornar um banco inesperado. `public` preexistente não é gerenciado por nós.
Se `catalog` já existir e estiver não vazio sem histórico Flyway, investigar e
planejar uma migration/baseline explícita; não aceitar automaticamente.

## Testes reproduzíveis

Da raiz do repositório, com Docker e Compose disponíveis:

```bash
bash scripts/test-database.sh
```

O script usa nome de projeto exclusivo, PostgreSQL 16 em tmpfs, sem portas
publicadas e sem os volumes da aplicação. Compila o estágio Maven/Java 21,
executa testes unitários e `mvn -B -Pintegration-tests verify`, e remove somente
os recursos dessa execução ao terminar. Retorna erro quando testes falham.
As primeiras imagens/dependências exigem download; os testes não consultam
serviços externos ou fontes de livros. O PostgreSQL deve estar disponível: o
perfil não transforma falta de banco em teste ignorado.

Com Maven e Java 21 instalados:

```bash
cd services/catalog-service
mvn test
# Usar apenas um PostgreSQL de teste vazio, nunca o banco de produção:
DB_HOST=localhost DB_NAME=bookrush_test DB_USERNAME=bookrush_test \
  DB_PASSWORD=test-only-not-for-production mvn -Pintegration-tests verify
```

## Evolução e recuperação

Não editar migrations aplicadas: Flyway valida checksums. Criar uma nova versão
para evoluir/reparar. O projeto não usa migrations down destrutivas. Rollback de
imagem Jenkins não desfaz DDL; migrations futuras precisam ser compatíveis com
a versão anterior durante a janela de rollback. Rebuild é permitido somente
nos recursos descartáveis de teste. Em produção, recuperação exige backup e
plano operacional, que será desenvolvido nos beads de operação.

- [Assets, versões e referências S3](storage-model.md): modelo implementado em V4.

- [Ingestão e metadata original](ingestion-model.md)
- [Processamento e linhagem](lineage.md)

As 14 entidades JPA estão em persistence/model; os três repositories em
persistence/repository. O teste IngestionLineageIT executa o próprio queries.sql
montado read-only pelo Compose. Executando Maven no diretório do serviço, ele
usa ../../docs/database/queries.sql. A fixture está em src/test/resources e
é revertida após cada teste; nunca é migration de produção.

- [Relatório de entrega do épico](report.md)
