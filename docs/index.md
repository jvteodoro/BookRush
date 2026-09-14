# BookRush Engineering Handbook

Git é a fonte canônica. Backstage é o portal oficial de descoberta e consulta.

- [Política de documentação](documentation-policy.md)
- [Modelo PostgreSQL](database/README.md)
- [Storage S3](storage/README.md)
- [Ingestão](ingestion/README.md)
- [Decisões arquiteturais](adr/README.md)
- [Incidentes de deploy](operations/deployment-incident-study.md)
- [Baseline da plataforma e autenticação](platform/baseline-inventory.md)
- [Plataforma Java compartilhada](platform/README.md)
- [Autenticação e autorização](auth/README.md)
- [OIDC do Jenkins com Keycloak](auth/jenkins-oidc.md)
- [Matriz de migração de realms](auth/realm-migration.md)
- [Ciclo de acesso administrativo](auth/access-lifecycle.md)
- [Inventário de interfaces](auth/interface-inventory.md)
- [Lifecycle e IaC das aplicações](platform/lifecycle.md)
- [Beads](beads.md)

O catálogo do portal registra separadamente os serviços e suas APIs; este handbook
preserva a documentação transversal já existente, sem copiar seu conteúdo.

## Analytics de conteúdo

A fundação do `book-analytics-service` usa o schema `analytics` e referencia a
versão textual exata do catálogo. Ela inclui jobs persistentes/idempotentes,
excerpts paginados, features determinísticas, ranker explicável, embeddings e
LLM desabilitados por padrão, métricas e benchmark offline. Consulte a página
do serviço no catálogo Backstage; a propriedade e os limites estão em
[ADR-004](adr/ADR-004-analytics-ownership.md).
