# Excerpt Feed Integration V1 — registro de execução

Este arquivo acompanha os beads importados de
`excerpt-feed-integration-v1/beads/excerpt-feed-integration-v1.jsonl`. O banco
Beads continua sendo o registro operacional; esta página é um índice legível e
versionado para o portal.

## Estado

| Bead | Entrega | Estado | Evidência |
| --- | --- | --- | --- |
| `feedex-v1-01` | Auditoria de contratos e ownership | `closed` | Auditoria confirmou BFF como pass-through, recommendation como owner do feed e analytics como owner dos excerpts. |
| `feedex-v1-02` | Contrato nullable `FeedExcerpt` | `closed` | `FeedExcerpt` preserva texto, hash, offsets, versão física, generator e rank opcional. |
| `feedex-v1-03` | Cliente analytics autenticado | `closed` | `AnalyticsExcerptClient` usa client credentials, audience/scope configuráveis e nunca recebe bearer do usuário. |
| `feedex-v1-04` | Elegibilidade estrutural | `closed` | Migration aditiva V14 e `StructuredContentEligibility`; endpoint filtra body elegível e versão normalizada atual. |
| `feedex-v1-05` | Seleção V1 | `closed` | Seleção prefere rank compatível e usa fallback determinístico sem inventar score. |
| `feedex-v1-06` | Enriquecimento paralelo limitado | `closed` | Executor limitado, orçamento total e timeout degradam cada item sem falhar o feed. |
| `feedex-v1-07` | Cache positivo/negativo | `closed` | TTLs separados e chave inclui livro/política; valor preserva `sourceAssetVersionId`. |
| `feedex-v1-08` | Circuit breaker/degradação | `closed` | Falhas S2S/analytics abrem circuito curto e retornam `excerpt` ausente. |
| `feedex-v1-09` | Propagação pelo BFF | `closed` | BFF continua repassando o mapa nullable e não consulta analytics. |
| `feedex-v1-10` | Renderização React | `closed` | Card usa excerpt > descrição > fallback; rank nunca é renderizado ao leitor. |
| `feedex-v1-11` | Eventos de excerpt | `closed` | Impressão por viewability e abertura enviam apenas IDs/versão, posição e duração; texto não é enviado. |
| `feedex-v1-12` | Observabilidade | `closed` | Métricas de attach/fallback/cache/latência foram adicionadas sem labels de texto ou usuário. |
| `feedex-v1-13` | Regressão TOC Frankenstein | `closed` | Fixture unitária rejeita `CONTENTS`/sequência de cartas/capítulos e aceita prosa de Chapter 5. |
| `feedex-v1-14` | Linhagem/troca de versão | `closed` | Query restringe à versão textual disponível mais recente (`NORMALIZED`, `PROCESSING` ou `ANALYTICS`, conforme o papel publicado pelo ingestion); cache é versionado por política e payload mantém hash/offsets. |
| `feedex-v1-15` | Integração processado/não processado | `closed` | Testes cobrem seleção de excerpt processado e `null` para lista sem analytics; fallback permanece no frontend. |
| `feedex-v1-16` | Outage e expiração S2S | `closed` | Teste MockRest valida outage/token inválido; cliente trata expiração e falha como fallback. |
| `feedex-v1-17` | Feature flags/rollout | `closed` | `FEED_EXCERPTS_ENABLED`, porcentagem e versão de ranker configuráveis; defaults são rollback imediato. |
| `feedex-v1-18` | Runbook e Backstage | `closed` | Índice, runbook do serviço, contrato OpenAPI, configuração e registro desta execução atualizados. |
| `feedex-v1-19` | Gate final | `closed` | Maven recommendation/analytics/BFF, build React, Docker build, Compose config, JSON e `git diff --check` passaram. |

## Critério de fechamento

Os estados acima correspondem à implementação e aos testes locais. Em
02/10/2026 o client técnico `bookrush-recommendation-s2s` foi reconciliado no
realm `bookrush-platform`, a migration V14 foi aplicada no PostgreSQL e os
containers de analytics, recommendation, BFF e frontend ficaram saudáveis.
O rollout está habilitado no `.env` do host com `FEED_EXCERPTS_ENABLED=true` e
porcentagem 100; o segredo permanece fora do Git. A consulta de analytics com
client credentials retornou HTTP 200 e lista vazia para o livro de teste, o que
é uma degradação válida quando não há excerpt elegível. A prova de feed com
bearer humano e um segundo livro ainda depende de uma sessão de produto
disponível; nenhum bulk foi iniciado. A validação autenticada do livro
processado `c099bc3c-e72a-478f-971e-0427c4957bd0` retornou HTTP 200 com um
excerpt elegível, incluindo `source_asset_version_id` e offsets code point.
