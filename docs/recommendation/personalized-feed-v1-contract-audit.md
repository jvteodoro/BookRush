# Personalized Feed V1 — audit de contratos no HEAD

Data do audit: 2026-10-20  
Branch: `feature/personalized-feed-v1`  
HEAD auditado: `9b88eaf` (`feature/feed-optimization-phase-2` local)

## Escopo e limitação da base

O pacote `../bookrush-personalized-feed-v1/` foi confrontado com o código do
HEAD, e não apenas com a documentação importada. A tentativa de consultar
`origin/main` falhou por permissão SSH do ambiente; portanto esta primeira
revisão foi criada sobre a revisão local disponível, preservando as mudanças
locais preexistentes. Antes de publicar ou disparar Jenkins, a branch deverá
ser sincronizada com a revisão remota autorizada e o SHA efetivo deverá ser
registrado.

Testes baseline executados antes desta alteração:

| Serviço | Comando | Resultado |
| --- | --- | --- |
| recommendation-service | `mvn -q test` | PASS |
| behavior-service | `mvn -q test` | PASS |
| reader-bff-service | `mvn -q test` | PASS |
| book-analytics-service | `mvn -q test` | PASS |

## Ownership confirmado

| Domínio | Dono atual | Interface observada |
| --- | --- | --- |
| Decisão do feed, ranking e ledger | recommendation-service | `/api/v1/recommendations/feed`, `/api/v1/recommendations/impressions/{impressionId}/viewable` |
| Fatos comportamentais e agregados | behavior-service | `POST /api/v1/behavior/events`, agregados e outbox |
| Score, excerpts, embeddings e versões de analytics | book-analytics-service | `POST /api/internal/v1/content-analytics/book-scores`, APIs de excerpts/resumo e jobs |
| Composição pública do leitor | reader-bff-service | `/api/v1/reader/feed`, estado de leitura, conteúdo e proxy de eventos pelo cliente |

Nenhum serviço deve acessar diretamente o schema de outro serviço para
implementar Personalization V1. As novas integrações devem ser APIs S2S com
audience/scope explícitos.

## Contrato atual do feed

O `recommendation-service` atende `GET /api/v1/recommendations/feed` e o
`reader-bff-service` expõe a mesma operação em
`GET /api/v1/reader/feed?size=N`.

O response atual contém `requestId`, `modelVersion`, `rankingVersion`,
`strategy`, `generatedAt` e `items`. Cada item contém o livro, `rank`,
`reason`, `modelVersion`, `recommendationRequestId`, `impressionId` e, quando
disponível, um excerpt.

O BFF encaminha o bearer recebido sem substituir a identidade e mantém o
limite público entre 1 e 100 itens. A requisição de catálogo atual é feita
para `page=0`, com no máximo 100 livros. Isso confirma o risco descrito no
pacote: o candidate pool atual está limitado à primeira página.

O ranking atual é `RecommendationHeuristic`. Ele usa `strategyFor` baseada
somente na quantidade histórica de impressões, score analítico global quando
disponível e um hash de `subject + bookId` para desempate/exploração. Isso não
é afinidade semântica usuário↔livro e não pode ser chamado de personalização
PFV1.

## Ledger atual e divergências encontradas

As migrations `V1`–`V4` do recommendation-service criam:

- `recommendation.request`: sujeito, modelo, estratégia, seed, contexto e
  `candidate_set_hash`;
- `recommendation.item`: livro, excerpt opcional, rank, score e `reason`;
- `recommendation.impression`: impressão viewable ligada ao request e ao item.

O fluxo atual gera `itemId` e `impressionId`, mas possui as seguintes lacunas:

1. `recommendation.item.score` é persistido como `1.0 / rank`, embora esse
   valor não seja o score efetivamente usado pelo ranker.
2. `candidate_set_hash` existe na migration, mas não é preenchido no fluxo
   atual.
3. `excerpt_id` existe no ledger, mas não é associado quando o excerpt é
   enriquecido.
4. Os componentes, versão de configuração e proveniência do score ainda não
   são persistidos como contrato estruturado do item.
5. O response público expõe IDs de correlação necessários, mas as ações
   posteriores do leitor ainda não carregam esses IDs para o behavior-service.

Esses pontos correspondem principalmente aos beads `pfv1-110` e `pfv1-140`.

## Eventos comportamentais reais

O endpoint `POST /api/v1/behavior/events` recebe lotes de até 100 eventos com:

```json
{
  "eventKey": "idempotency-key",
  "eventType": "BOOK_OPEN",
  "bookId": "uuid-ou-null",
  "occurredAt": "timestamp",
  "payload": {}
}
```

O `identity_subject` é derivado do principal autenticado no servidor; valores
de identidade enviados no payload são ignorados. O behavior-service persiste
o evento e o outbox na mesma transação.

Eventos encontrados no código e nos agregados atuais:

- `BOOK_OPEN`
- `READ_PROGRESS`
- `READ_SESSION`
- `BOOK_COMPLETE`
- `EXCERPT_IMPRESSION`
- `BOOK_LIKE` e `BOOK_UNLIKE`
- `BOOK_SHARE`
- `BOOK_COMMENT`

O frontend e o reader-state já emitem diretamente `BOOK_OPEN`,
`READ_PROGRESS`, `READ_SESSION` e `EXCERPT_IMPRESSION`. A estrutura atual não
possui campos de primeira classe para `recommendationItemId` ou
`impressionId`; eles deverão ser adicionados ao payload de correlação com
validação de pertencimento ao sujeito autenticado.

Há agregados diários (`daily_book` e `daily_user_book`) e outbox, mas ainda
não há uma projeção serving específica `user_book_signal` com estado correto
para sinais reversíveis como `LIKE/UNLIKE` e `SAVE/UNSAVE`. Isso é o núcleo do
bead `pfv1-120`.

## Contrato atual de analytics

O recommendation-service chama, via `AnalyticsScoreClient`,
`POST /api/internal/v1/content-analytics/book-scores` com até 100 UUIDs.
O response contém `requested`, `returned` e itens com `bookId`, `score` e
`rankerVersion`.

O book-analytics-service seleciona o maior score elegível da versão textual
atual de cada livro. As configurações atuais preservam:

```properties
ANALYTICS_BOOK_SCORES_STRATEGY=CURRENT
ANALYTICS_BOOK_SCORES_JIT=DEFAULT
```

O armazenamento de embeddings e excerpts permanece dono do
book-analytics-service. O recommendation-service não deve copiar vetores para
seu banco; o snapshot semântico de PFV1 deve ser exposto por contrato S2S
versionado, conforme `pfv1-210`.

## Flags e estado de rollout

O ranking analítico atual é controlado por:

- `FEED_ANALYTICS_RANKING_ENABLED`;
- `FEED_ANALYTICS_RANKING_MODEL_VERSION`;
- URL, audience, scope e credenciais S2S do analytics.

Essas flags controlam analytics-aware-v1, mas não existe ainda uma flag
explícita `OFF/SHADOW/ON` para `PERSONALIZED_V1`. Isso deverá ser criado em
`pfv1-370`, com fallback controlado para analytics-aware-v1.

## Matriz de mudança PFV1

| Bead | Mudança necessária após o audit |
| --- | --- |
| `pfv1-110` | Persistir score real, componentes, versões, config hash, excerpt e candidate hash no ledger |
| `pfv1-120` | Criar projeção serving de sinais por usuário/livro em behavior |
| `pfv1-130` | Expor sinais por API S2S least-privilege |
| `pfv1-140` | Propagar e validar `recommendationItemId`/`impressionId` até attribution |
| `pfv1-150` | Adicionar gates de qualidade e métricas de sinais/attribution |
| `pfv1-210` | Expor snapshot semântico de livro com versão e idade |
| `pfv1-220` | Cache local/versionado de features, sem JDBC cross-schema |
| `pfv1-230` | Construir perfil semântico versionado a partir de sinais e snapshots |
| `pfv1-240` | Confiança, cold start, blend e lifecycle do perfil |
| `pfv1-250` | Fixtures sintéticas determinísticas para perfis |
| `pfv1-310` | Candidate retrieval além da página 0 |
| `pfv1-320` | União global, semântica, trending e exploração |
| `pfv1-330` | Fadiga por exposição/repetição |
| `pfv1-340` | Score explicável com afinidade, qualidade, tendência, exploração e fadiga |
| `pfv1-350` | Diversidade semântica no nível de livro |
| `pfv1-360` | Exploração determinística e configurável |
| `pfv1-370` | Integração segura, fallback e rollout por flag |
| `pfv1-380` | Métricas de cobertura, confiança, latência e replay |
| `pfv1-410` | E2E com dois usuários sintéticos e cold start |
| `pfv1-420` | Shadow e ativação gradual, reconciliando `bookrush-b788` |
| `pfv1-430` | Feed Lab com sessões versionadas, reconciliando `bookrush-cmyr` |
| `pfv1-440` | Dataset temporal com decisão→outcome, sem leakage |
| `pfv1-450` | Gate final, documentação, pipeline, smoke e rollback |

## Próximo passo

O audit atende o objetivo do `pfv1-101` e não implementa personalização
prematuramente. A sequência técnica começa por `pfv1-110`, `pfv1-120` e
`pfv1-130`, mantendo o feed com a política atual até que os sinais tenham
contrato, testes e fallback. Nenhum modelo LTR, vector database ou ativação
`PERSONALIZED_V1` deve ser introduzido nesta etapa.
