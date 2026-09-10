# Modelo do catálogo

| Kind | Responsabilidade | Exemplos reais |
|---|---|---|
| Domain | fronteira de negócio | book-platform |
| System | capacidade agrupada | book-catalog, identity-access, platform-infrastructure |
| Component | código ou serviço operável | catalog-service, book-ingestion-service, bookrush-frontend, keycloak, jenkins, developer-portal, platform-handbook |
| API | contrato machine-readable | catalog-api, book-ingestion-api |
| Resource | infraestrutura consumida | books-postgres, books-object-storage, books-redis, backstage-postgres |
| Group | ownership de equipe | book-platform, platform-engineering |

System recommendation está explicitamente planejado e não tem Component fictício.
API não é filha de Resource: `providesApis`/`consumesApis` e `dependsOn` representam
relações diferentes; Domain → System → Component expressa agrupamento.

A ingestão consome catalog-api e depende do PostgreSQL/S3/Redis existentes.
Frontend consome catalog-api. O portal depende apenas de seu PostgreSQL.
Todos os owners usam refs Group, nunca nomes pessoais.

## Equipes e recursos

Crie YAML Group em catalog/groups, inclua no Location raiz e execute validação.
Grupos usam spec.type=team, children=[] e profile.displayName. Usuários reais,
quando necessários para login, entram como User com spec.profile.email e memberOf.
Não invente pessoas ou credenciais. Só usuários cujo email corresponda ao Keycloak
podem autenticar no perfil público.

Resource requer type, owner e system existentes. Não cadastre Kafka ou Elasticsearch
só por constarem na visão futura: atualmente não estão no Compose funcional.
