# Architecture Decision Records

Uma decisão significativa recebe o próximo número livre `ADR-NNN-title.md`.
Preserve ADRs aceitos: uma mudança de decisão cria outro ADR que declara qual substitui.
ADRs de ingestão existentes permanecem na árvore ingestion/adr e no TechDocs do handbook.

## Template

```markdown
# ADR XXXX — Title
Status: Proposed | Accepted | Superseded
Date: YYYY-MM-DD

## Context
Problema e restrições observadas.
## Decision
Decisão e escopo.
## Alternatives Considered
Alternativas e tradeoffs técnicos.
## Consequences
Benefícios e custos.
## Risks
Riscos e mitigação.
## References
Código, contratos e decisões relacionadas.
```

- [Modelo de dados](ADR-001-book-storage-data-model.md)
- [Storage](ADR-002-object-storage-s3.md)
- [Developer Portal](ADR-003-developer-portal.md)
