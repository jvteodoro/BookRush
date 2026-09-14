# ADR-005 — Armazenamento inicial de embeddings

Status: Accepted  
Date: 2026-09-14

## Context

Embeddings são artefatos grandes e dependem da dimensão/modelo. A imagem
PostgreSQL atual não possui pgvector fixado, e adicionar uma extensão sem provar
backup/restore e índices ANN poderia quebrar upgrades.

## Decision

Nesta fase, PostgreSQL guarda somente `embedding_model`, input hash, dimensão,
versão e referência `storage_provider + bucket + object_key`. O vetor binário
fica no object storage `books-ml`. Busca exata para fixtures pequenas pode ser
implementada pelo worker após baixar os artefatos. pgvector será reavaliado
quando houver volume medido e imagem fixada; a migration não usa JSONB como
substituto silencioso de vetor.

## Consequences

O modelo é portátil entre SeaweedFS e S3, mas uma busca ANN exige uma etapa
posterior ou índice externo. Hash/model/version impedem reutilizar embedding de
texto diferente.

## References

- `services/book-analytics-service/src/main/resources/db/migration/V1__analytics_foundation.sql`
- `docs/adr/ADR-004-analytics-ownership.md`
