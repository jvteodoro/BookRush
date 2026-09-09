# Entrega da fundação PostgreSQL — bookrush-br1

## Schema e arquivos

14 tabelas: book, edition, author, book_author, edition_author, source, license,
external_identifier, book_asset, book_asset_version, source_record, ingestion_job,
ingestion_item, asset_processing. O [ER completo](schema.md) corresponde a essas
tabelas. Não foram criadas tabelas de capítulos, trechos ou analytics.

Migrations em services/catalog-service/src/main/resources/db/migration:
V1__initialize_catalog, V2__bibliographic_catalog, V3__sources_licenses_identifiers,
V4__asset_versions, V5__ingestion_provenance, V6__asset_processing (arquivos .sql).

Entidades em persistence/model: Book, Edition, Author, BookAuthor, EditionAuthor,
Source, License, ExternalIdentifier, BookAsset, BookAssetVersion, SourceRecord,
IngestionJob, IngestionItem, AssetProcessing; enums por nome e StorageProvider.
Repositories: BookRepository, BookAssetRepository, BookAssetVersionRepository.
Não existem repositories vazios para tabelas sem consulta de aplicação.

pom.xml adiciona Flyway/PostgreSQL e perfil Failsafe; application.yml configura
schema, Flyway sem baseline/clean e Hibernate validate. Compose de teste e
scripts/test-database.sh isolam PostgreSQL e executam Maven. queries.sql é
montado read-only para testar os mesmos exemplos publicados.

## Decisões, constraints e índices

Obra difere de edição; asset difere de versão. UUID interno; créditos de edição
separados; FKs reais com XOR para IDs externos. Hash lowercase não unique;
provider S3 e bucket/key substituem URLs. Permissões desconhecidas são NULL.
Migrations preservam metadata incompleta e não armazenam blobs.

Constraints: edição/obra coerentes, origem/registro/ID de ingestão coerentes,
versão positiva, key física única, SHA-256 válido, metadata obrigatória em
AVAILABLE, contadores de tentativas coerentes, datas ordenadas, enums textuais,
FKs RESTRICT e autoaresta de processamento proibida.

[Índices](indexes.md) cobrem título, relação obra/edição, créditos reversos,
IDs externos, assets por origem/obra, versões por hash/disponibilidade,
último job e falhas, proveniência e arestas de processamento. PK/unique são
reutilizados. EXPLAIN é executado sem alegar desempenho de produção.

## Exemplo Gutenberg

Fixture: source GUTENBERG → external_identifier GUTENBERG_ID 1342 →
book Pride and Prejudice, author Jane Austen → edition inglesa →
EPUB SOURCE → book_asset_version v1 →
S3/books-source/books/30000000-0000-4000-8000-000000000001/fixture/1/v1/book.

A versão aponta à tentativa, que aponta à revisão JSONB da fonte. As outras
versões representam EPUB público normalizado, TXT e chapters JSON, ligadas por
NORMALIZATION, TEXT_EXTRACTION e CHAPTER_SEGMENTATION. Uma v2 falha não substitui
a v1 disponível. As keys são referências de fixture, não objetos S3 criados.

## Documentação

- README: iniciar, testar e recuperar sem limpar dados reais.
- schema: tabelas, ER e integridade.
- entities: campos, obrigatoriedade, tipos e relacionamentos JPA.
- storage-model: identidade, versões, estados, licença e reconciliação futura.
- ingestion-model: jobs, tentativas e revisões da origem.
- lineage: transformações, retries e ciclos.
- indexes: consultas beneficiadas e índices omitidos.
- queries.sql: oito consultas executadas nos testes.
- validation: comandos, evidências e limites.
- ADR-001: decisões, alternativas e extensões adiadas.
- README raiz: link para esta documentação.

## Executar

Com Docker/Compose, na raiz do clone:

```bash
bash scripts/test-database.sh
```

Para executar a aplicação, seguir o .env e Compose do README raiz. O backend
aplica migrations no banco configurado no início. Esta entrega não fez deploy.
Com Java 21/Maven local, executar mvn test no serviço; integração exige banco
descartável e as variáveis documentadas em README.md desta pasta.

## Limites e decisões adiadas

A fundação de dados não implementa SeaweedFS, SDK, uploads, autorização, crawlers
ou processadores: pertencem ao épico bookrush-0k2 e pipelines futuros. JPA e SQL
foram testados em PostgreSQL real; nenhuma referência de fixture prova objeto S3.

Capítulos/trechos foram deliberadamente adiados, conforme escopo permitido.
Offsets futuros precisam referenciar versão/unidade de texto. Ciclos indiretos
são detectados nas consultas, não proibidos concorrentemente pela DDL.
Máquinas de estado, cálculo de hash, canonicalização do JSON/IDs, checksum ISBN,
resolução de conflitos e imutabilidade física são contratos de serviços futuros.
Não há XA, cascades históricos, recomendação ou blobs.

Rollback de aplicação não desfaz migration. Rebuild foi validado em banco
descartável; não há down migration destrutiva. Restore de banco real e backup
coordenado S3 pertencem à operação de storage. Não houve commit, push, sync
remoto dos beads ou deploy desta implementação.
