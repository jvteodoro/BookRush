# Baseline da plataforma e autenticação

Inventário iniciado em 2026-09-14 para o épico `bookrush-nf8` (plataforma
Java compartilhada e login BookRush). A matriz distingue o que existe no
checkout daquilo que ainda é trabalho dos beads PA-02–PA-23.

| Requisito | Estado observado | Lacuna | Evidência / alteração prevista |
|---|---|---|---|
| Java e build | Java 21; Maven 3.9; cada serviço possui POM próprio e parent `spring-boot-starter-parent` 3.3.5; plataforma em `platform/` com BOM e bibliotecas | Build limpo depende de Maven/Docker acessíveis para prova completa | `platform/pom.xml`, Dockerfiles raiz-contextualizados; PA-04–PA-18 |
| Coordenadas | `com.bookrush:catalog-service:0.0.1-SNAPSHOT`, `book-ingestion-service:0.1.0-SNAPSHOT`, `book-analytics-service:0.1.0-SNAPSHOT` | Versões técnicas repetidas entre POMs | PA-03/PA-10 |
| Dependências externas | AWS SDK 2.31.54, Springdoc 2.6.0; ingestão usa DuckDB `1.3.2.0` | Não há gerenciamento central | POMs reais; PA-03 deve preservar DuckDB corrigido |
| Frontend | React 18 + Vite 5 + TypeScript 5; cliente OIDC `bookrush-web` usa Authorization Code + PKCE e armazenamento de token em memória | E2E real com Keycloak depende de ambiente Docker/IdP disponível | `frontend/src/auth.ts`, `frontend/src/main.tsx`; PA-12/PA-13 |
| IdP | Keycloak 25 no Compose; realm produto `bookrush` e realm administrativo `bookrush-platform`; configuração incremental via keycloak-config-cli | Memberships reais, MFA e reconciliação online ainda exigem execução autorizada | `infrastructure/keycloak/*.json`, `infrastructure/keycloak/reconcile.sh`; PA-11, EXT-01/03/07 |
| Ingestão | Resource Server com issuer/audience/JWKS configuráveis; rotas administrativas usam roles do realm administrativo; client credentials S2S separados e fallback legado opt-in | Prova online de renovação/escopo e retirada final do legado ainda pendente | `IngestionSecurityConfiguration`, `OidcServiceTokenProvider`, `docs/auth/credential-transition.md`; PA-15/PA-16 |
| Catálogo/assets | Rotas públicas intencionais; administração de assets aceita `ASSET_ADMIN` humano ou scope técnico de upload; comandos internos aceitam role/scope canônico | Prova online de audience/issuer e migração persistente ainda pendente | `AssetSecurity`, `CatalogOidcProperties`, `application.yml`; PA-14/PA-16 |
| Analytics | Resource Server condicional para scope `bookrush.analytics`; worker e schema próprios | Ainda não consome starter compartilhado | `AnalyticsSecurityConfiguration`; PA-07/PA-10 |
| Observabilidade | Actuator/Micrometer e logs existentes; starter compartilhado valida/propaga `X-Correlation-ID` e limpa MDC | Métricas de IAM online ainda não foram exercitadas | `bookrush-observability-spring-boot-starter`; PA-08 |
| Gateway | Nginx termina TLS; Traefik usa provider de arquivo em loopback; prefixos `/api`, `/ingestion`, `/analytics` e hosts dedicados já existem | Novos callbacks OIDC e hosts precisam ser adicionados sem expor portas internas | `infrastructure/traefik/dynamic.yaml`, `infrastructure/compose.yaml`; PA-17 |
| Backstage | Location raiz resolve Groups, Systems, Components, APIs e TechDocs; Git é fonte canônica | Plataforma e cliente de produto ainda não estão catalogados | `catalog-info.yaml`, `backstage/catalog/`; PA-21 |
| CI | Jenkinsfiles principal e cópia de bootstrap permanecem idênticos; Dockerfiles de Java usam contexto raiz e instalam a plataforma | Execução depende de daemon Docker/Jenkins; checks de drift/SSO ainda pendentes | `Jenkinsfile`, `infrastructure/jenkins/bookrush.Jenkinsfile`; PA-22, EXT-04/08 |
| Beads | 23 tarefas PA-01–PA-23 importadas com dependências topológicas e agora filhas de `bookrush-nf8` | Outros épicos históricos (`bookrush-5p7`, `bookrush-da00`) não fazem parte desta onda | `bd show`, `bd list --json`; não duplicar tarefas |

## Identidade e chaves de idempotência

O ingestion-service recebe `Idempotency-Key` e calcula hash do request. O
principal atualmente pode vir do contexto OIDC ou do header de compatibilidade
`X-Principal` no modo legado/fixture; a migração deve tornar `(iss, sub)` a
identidade estável sem usar e-mail. O adapter canônico envia
`Authorization: Bearer` e `X-Canonical-Service-Token`; o segundo é compatibilidade
legada, não uma credencial de usuário.

## Armazenamento e fronteiras

PostgreSQL 16 usa volumes Docker persistentes. O catálogo é dono do schema
`catalog`; analytics possui o schema `analytics`; ingestão coordena jobs e
staging. SeaweedFS fornece S3-compatible storage com buckets privados. Nenhuma
biblioteca compartilhada deve introduzir entidades JPA, migrations, clientes S3
ou conexão obrigatória com banco.

## Ordem da implementação

PA-01 registra este baseline. PA-02 define as decisões; PA-03 cria o reactor/BOM;
PA-04–PA-09 criam bibliotecas técnicas e testes; PA-10 integra catálogo e
ingestão; PA-11–PA-17 implementam Keycloak, frontend, APIs e transição S2S;
PA-18–PA-23 validam consumidor externo, regressões, Backstage, CI e rollout.
