# Relatório de pendências da productização

Atualizado em 2026-10-01. O épico `bookrush-slos` permanece aberto. Este relatório reflete o estado do banco Beads e o código presente no checkout; itens parciais não são tratados como concluídos.

## Entregue e validado

- ADR de bounded contexts e identidade (`docs/adr/ADR-0034-product-bounded-contexts.md`).
- Nove serviços Spring Boot 3.3.5/Java 21 com Dockerfiles, migrations iniciais, health endpoints, catálogo Backstage e TechDocs.
- Compose e Traefik com declarações e healthchecks dos novos serviços.
- Catálogo público paginado com busca por título e identificador.
- API inicial de conteúdo que resolve metadados por HTTP do catálogo.
- Biblioteca e progresso persistidos por identidade no `reader-state-service`.
- Perfil persistido no `reader-profile-service`.
- Likes, comentários, follows e reports persistidos no `social-service`.
- Recepção idempotente de eventos no `behavior-service`.
- Agregados diários reconstruíveis de eventos.
- Feed inicial `heuristic-v1` com request ID, impression ID, versão e rank.
- APIs iniciais de publisher, administração, moderação e BFF.
- Exportador offline com manifesto, cutoff, row count e SHA-256.
- Web e mobile enviam ações básicas à API; o Admin Web usa mocks somente quando explicitamente configurado.

## Pendências por bead

| Bead | Situação | O que falta para fechar |
|---|---|---|
| `bookrush-01se` | Parcial | Ler artefatos normalizados reais, capítulos, offsets, reader-ready e URLs temporárias S3. |
| `bookrush-tdn2` | Parcial | Bookmarks completos, recentes, sessões, streak, plant projection e testes de concorrência. |
| `bookrush-7vbg` | Parcial | Moderação completa, shares, consulta de follows, autorização por recurso e eventos de domínio. |
| `bookrush-5kch` | Parcial | Schema registry, rejects, outbox/polling, retenção, métricas e particionamento operacional. |
| `bookrush-013b` | Parcial | Agregados user-book, atribuição de outcomes, jobs agendados e endpoints de dashboard. |
| `bookrush-uzrd` | Parcial | Consulta real ao PostgreSQL, escrita no SeaweedFS/S3, manifest assinado e teste de restauração. |
| `bookrush-pw9f` | Parcial | Ledger persistente de requests/impressions, diversidade, cold-start, lucky exploration e eventos. |
| `bookrush-l2dv` | Parcial | Composição real de catálogo, estado, perfil e recomendações com timeouts e degradação. |
| `bookrush-52i4` | Parcial | SDK compartilhado web/mobile, viewability, dwell, batching, retry e deduplicação. |
| `bookrush-1cc7` | Aberto | Remover handlers mockados do PageLoop e conectar feed, comentários, share, perfil e biblioteca às APIs. |
| `bookrush-4tbl` | Aberto | Substituir estado local do mobile, usar feed/BFF, reader, biblioteca, progresso e telemetria reais. |
| `bookrush-ifms` | Parcial | Upload staging, signed URLs, membership, workflow de aprovação e orquestração de ingestão. |
| `bookrush-haci` | Aberto | Conectar métricas reais por publisher e remoção dos números estáticos do portal. |
| `bookrush-nuc9` | Parcial | Facade Keycloak para habilitar/desabilitar usuários, KPIs reais, autorização administrativa e auditoria completa. |
| `bookrush-c26g` | Aberto | Ligar todas as telas Admin aos endpoints reais e remover datasets mockados residuais. |
| `bookrush-pzlf` | Parcial | Reconciliar os novos clients/roles no Keycloak persistente e provar scopes/audiences S2S. |
| `bookrush-qxf3` | Parcial | Busca por autor, subject e keywords com índices e contrato atualizado. |

## Pendências de integração e validação

1. Testes de integração com PostgreSQL/Keycloak via Compose descartável.
2. Testes de segurança para produto, administração e clients S2S.
3. Testes E2E web e mobile contra APIs reais.
4. Teste de migração Flyway do zero e upgrade de volumes existentes.
5. Smoke através do Traefik para cada rota nova.
6. Verificação de persistência após restart dos containers.
7. Testes de retry, idempotência, timeout e indisponibilidade de dependências.
8. Exportação real para S3/SeaweedFS e leitura posterior do artefato.
9. Atualização dos OpenAPI dos serviços novos com schemas e security schemes completos.
10. TechDocs específicos de operação, backup/restore e troubleshooting de cada serviço.

## Ordem recomendada

1. Fechar o `reader-state-service` e `book-content-service`, pois o leitor depende deles.
2. Persistir o ledger do recommendation-service e completar o BFF.
3. Completar behavior, agregados e export S3.
4. Conectar web e mobile e remover mocks restantes.
5. Completar publisher e admin com autorização real.
6. Reconciliar Keycloak e executar a matriz S2S.
7. Rodar E2E, resiliência, segurança e persistência; somente então fechar o épico.

## Evidências executadas até este relatório

- `npm run build --prefix frontend`
- `npm run build --prefix admin-web`
- `npm run typecheck --prefix mobile`
- `mvn package -DskipTests` nos serviços alterados
- `docker compose -f infrastructure/compose.yaml --env-file .env config --quiet`
- `node backstage/scripts/validate.mjs`
- `git diff --check`
- Smoke offline do exportador com manifesto e SHA-256

Essas verificações confirmam compilação, configuração e contratos básicos. Elas não substituem os testes E2E e operacionais listados acima.
