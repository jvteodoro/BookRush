# ADR-004 — Ownership do analytics de conteúdo

Status: Accepted  
Date: 2026-09-14

## Context

O `catalog-service` é o dono do schema `catalog` e do histórico bibliográfico.
Analytics precisa preservar múltiplas execuções, versões de analisadores,
excerpts e embeddings sem alterar o catálogo ou apagar resultados históricos.
Misturar essas tabelas no ingestion-service dificultaria deploy, replay e
retenção independentes.

## Decision

Criar o `book-analytics-service`, dono do schema `analytics` e de suas próprias
migrations Flyway. O serviço lê referências do catálogo por FKs reais no mesmo
PostgreSQL e grava exclusivamente em `analytics`. A entrada é sempre uma
`catalog.book_asset_version`, nunca uma `book` abstrata. Features são tabelas
relacionais tipadas por escopo; não usamos `entity_type/entity_id` polimórfico.

Embeddings começam com metadados no PostgreSQL e artefatos no object storage
(`bucket` + `object_key`). A adoção de pgvector fica para uma decisão posterior,
após medir volume, versão da imagem PostgreSQL e backup/restore. Nenhum modelo
remoto é obrigatório para executar o pipeline base.

## Alternatives Considered

Adicionar analytics ao catalog-service reduziria um container, mas violaria a
fronteira de ownership e acoplaria releases. Um banco separado desde o primeiro
passo aumentaria a superfície operacional e impediria FKs reais entre os dois
contextos. JSONB para todas as observações perderia constraints e consultas
simples.

## Consequences

Há uma migration e um serviço adicionais, mas resultados antigos podem coexistir
com novas versões de analyzer e de texto. O deploy precisa aplicar o catálogo
antes de analytics. Artefatos grandes exigem reconciliação com object storage.

## Risks

Uma FK entre schemas pressupõe que os dois schemas estejam no mesmo PostgreSQL;
se houver separação futura, ela será substituída por contrato de leitura e
verificação de existência. A tabela de excerpts valida offsets e hashes, mas a
verificação de que o texto não mudou depende do worker que lê a versão física.

## References

- `services/book-analytics-service/src/main/resources/db/migration/V1__analytics_foundation.sql`
- `docs/ingestion/processing.md`
- `docs/database/lineage.md`
