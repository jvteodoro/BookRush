# ADR 003 — Backstage e documentação como código

Status: Accepted
Date: 2026-09-10

## Context

O monorepo possui catálogo, ingestão, frontend e documentação transversal dispersa.
Precisamos descobrir ownership, APIs e operação sem depender do autor nem duplicar páginas.

## Decision

Backstage 1.54 é um aplicativo separado em backstage, criado pelo gerador oficial
0.9.1. Usa Catalog, TechDocs, API Docs, Search PostgreSQL e Scaffolder oficiais.
Git guarda YAML, Markdown, contratos e diagramas. Um Location raiz agrega o monorepo;
novos repositórios adicionam Locations Git. O handbook expõe docs existentes.
Node 24 e Yarn 4 são isolados dos serviços Java. Compose do portal tem PostgreSQL
próprio e porta loopback. Guest limita-se ao desenvolvimento; ambiente público usa
OIDC Keycloak e usuários explicitamente registrados no catálogo.

TechDocs local usa MkDocs instalado, sem socket Docker no backend. Produção usa
build externo na CI e publisher S3; artefatos são derivados, nunca fonte canônica.
Diagramas Mermaid são renderizados no build para SVG, sem serviço remoto de renderização.

## Alternatives Considered

Wiki independente fragmentaria review e contratos. Apenas MkDocs não representa
relações de ownership e dependência. Plugins para cada ferramenta aumentariam manutenção
sem necessidade atual. Compartilhar banco do catálogo acoplaria deploy e permissões.

## Consequences

Documentação e software evoluem juntos e podem ser revisados offline. Há custo de
Node, Python e build de documentação. Catálogo e busca são índices reconstruíveis;
credenciais, usuários e histórico de tarefas requerem backup do PostgreSQL do portal.

## Risks

Scaffolder pode escrever no Git: escopos devem ser mínimos e publicação usar PR.
Guest não deve ser exposto em rede pública. Contratos exportados podem divergir do código:
o fluxo de atualização exige gerar Springdoc da mesma revisão e revisar diff em CI.

## References

- https://backstage.io/docs/getting-started/
- https://backstage.io/docs/features/techdocs/architecture/
- https://backstage.io/docs/auth/oidc/
