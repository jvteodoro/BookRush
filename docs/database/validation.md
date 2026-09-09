# Validação final do schema — 2026-09-09

## Comandos e resultados

Da raiz:

```bash
bash -n scripts/test-database.sh
git diff --check
bash scripts/test-database.sh
```

Todos passaram. O script construiu a imagem Maven/Java 21 (mvn package), iniciou
PostgreSQL 16 descartável e executou mvn -B -Pintegration-tests verify:
**BUILD SUCCESS, 2 testes unitários + 34 de integração, zero falhas, erros ou
testes ignorados**. Containers e rede exclusivos foram removidos com exit 0;
nenhum volume da aplicação foi usado.

Também executado, offline e sem rede, na imagem criada por essa execução:

```bash
docker run --rm --network none bookrush-db-test-fl5for62-tests:latest mvn -B -o test
```

Resultado: BUILD SUCCESS, 2 testes unitários, zero falhas/erros/skips.
O nome de imagem é específico dessa execução; para reproduzir use o script
acima ou mvn test no diretório do serviço com Java 21/Maven.

## Cobertura executada

| Classe | Testes | Evidência |
|---|---:|---|
| StatusControllerTest | 2 | API de status preservada |
| MigrationIT | 2 | Flyway, no-op e configuração sem clean/baseline |
| CatalogSchemaIT | 8 | Core, dados incompletos, FK/check/unique, timestamps, upgrade V1→atual |
| SourceIdentifierSchemaIT | 8 | Alvos/formatos/uniques, licenças desconhecidas, upgrade V2→V3 |
| AssetSchemaIT | 6 | Versões, hash/key, edição/obra, estados, latest |
| IngestionLineageIT | 6 | Oito queries reais + EXPLAIN, retries, provenance, checks e ciclos |
| JpaCatalogIT | 3 | 14 entidades validadas, UUID/enum/JSON/timestamps/crédito round-trip, três repositories e LAZY |
| SchemaUpgradeIT | 1 | V4→V6 preserva asset/key; reexecução sem mudanças |

Fixture de teste: Jane Austen/Pride and Prejudice/Gutenberg 1342, edição,
origem JSONB, tentativa bem-sucedida e falha, quatro assets e suas transformações.
Uma versão v2 falha valida que latest continua na v1 disponível.
queries.sql é montado diretamente no container; os testes executam e verificam
as oito consultas publicadas, incluindo linhagem com ciclo e metadados de origem.

Na primeira execução da suíte nova, o teste LAZY falhou porque uma consulta
anterior já tinha carregado o asset no mesmo EntityManager. Foi corrigido com
clear antes da verificação; a suíte completa foi repetida e passou. Não houve
falha de migration ou de validação Hibernate nessa execução inicial.

## Limites

Nenhuma migration foi aplicada ao banco em uso, nem houve deploy, commit ou
push. Não há objetos S3 reais na fixture; não foram testados SeaweedFS/SDK/APIs
de assets, que pertencem ao épico de storage. Testes usam imagens/dependências
locais após preparação; o primeiro build pode precisar de download, mas nenhum
teste depende de internet, Gutenberg ou outro serviço externo.

Não há rollback DDL destrutivo. Testou-se rebuild descartável e upgrade; restore
de backup de produção não foi executado. Nenhum benchmark de escala foi feito:
EXPLAIN numa fixture pequena só valida execução/plano, não latência em produção.
