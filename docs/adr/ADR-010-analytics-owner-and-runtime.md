# ADR 010 — Ownership and runtime do Content Analytics V1
Status: Accepted
Date: 2026-09-15

## Context
O repositório já possui o `book-analytics-service`, responsável pelo schema `analytics`, jobs persistentes e APIs. Criar um segundo serviço escrevendo as mesmas tabelas quebraria a propriedade do bounded context e a linhagem histórica.

## Decision
Estender o `book-analytics-service` como único owner do schema e do ciclo de jobs. A implementação V1 mantém a API Spring Boot existente e usa contratos versionados em `services/book-analytics-service/config`. Integrações Python/ML, quando habilitadas, serão workers internos com protocolo explícito e escrita mediada pelo owner; não haverá outro migrador ou writer independente.

O pipeline base funciona sem download de modelos nem LLM remoto. Métricas determinísticas e estatísticas são o primeiro estágio; modelos locais só executam após preparação explícita e registram identidade, revisão e hash.

## Alternatives Considered
- Criar `book-content-analytics-service` Python separado: rejeitado por duplicar ownership e migrations existentes.
- Colocar analytics no catálogo: rejeitado porque analytics possui ciclo de vida, modelos e armazenamento próprios.

## Consequences
Preservamos APIs, migrations e jobs já validados. O serviço pode evoluir para workers Python sem migrar o schema. A execução de modelos exige artefatos preparados no host.

## Risks
A manutenção de dois runtimes (Java owner e workers Python) exige contrato de protocolo e validação de versões. Fica mitigado por configuração versionada e análise offline desabilitada por padrão.

## References
- `services/book-analytics-service`
- `services/book-analytics-service/config/analytics-spec-v1.yaml`
- `docs/adr/ADR-004-analytics-ownership.md`
