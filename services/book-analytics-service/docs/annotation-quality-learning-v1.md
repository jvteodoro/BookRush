# Excerpt Quality Learning V1

Esta milestone cria ground truth humano operacional para avaliar excerpts. Ela
fica no `book-analytics-service`, que já é o único dono do schema `analytics`, e
usa o admin web existente em `/admin/training`.

## Fluxo cego

1. Um operador cria uma campanha em estado `DRAFT`.
2. A configuração de sampling é congelada por hash quando a campanha inicia.
3. O annotator reivindica um item com lease temporário.
4. A primeira impressão e as seis dimensões ordinais (1–5) são registradas.
5. `LOCK_PRIMARY_ANNOTATION` torna os labels primários imutáveis.
6. Só depois do lock o contexto é liberado.
7. O diagnóstico de contexto e o submit são armazenados separadamente.
8. Discordâncias podem gerar adjudicação; adjudicação nunca altera labels brutos.

Previsões do modelo, scores NLI, protótipos e rank heurístico não devem ser
enviados pelo endpoint cego. O endpoint de contexto só responde depois de
`primary_locked_at` existir.

## API

Principais rotas autenticadas:

```text
POST /api/admin/v1/annotation/campaigns
GET  /api/admin/v1/annotation/campaigns
POST /api/admin/v1/annotation/campaigns/{id}/start|pause|complete|cancel
POST /api/admin/v1/annotation/campaigns/{id}/claim-next
POST /api/admin/v1/annotation/campaigns/{id}/sample
GET  /api/admin/v1/annotation/items/{id}
POST /api/admin/v1/annotation/items/{id}/first-screen
POST /api/admin/v1/annotation/items/{id}/lock
GET  /api/admin/v1/annotation/items/{id}/context
POST /api/admin/v1/annotation/items/{id}/submit
POST /api/admin/v1/annotation/items/{id}/adjudicate
GET  /api/admin/v1/annotation/campaigns/{id}/metrics
GET  /api/admin/v1/annotation/campaigns/{id}/adjudication-queue
POST /api/admin/v1/annotation/campaigns/{id}/datasets
GET  /api/admin/v1/annotation/campaigns/{id}/datasets/{version}/export
```

O subject vem do JWT validado. O browser não pode escolher o annotator. Os
grupos Keycloak `excerpt-annotators`, `excerpt-reviewers`, `operators`,
`platform-admins` são convertidos em authorities específicas do serviço.
O primeiro-screen retorna o texto e seu hash, mas não retorna rank, NLI,
protótipos ou embeddings; esses dados continuam ocultos até o lock.
Após `claim-next`, o admin consulta o item reservado para carregar esse texto
cego antes de renderizar os controles de rating.

## Piloto

`config/pilot-v1.yaml` prepara 50 excerpts EN e 50 PT, três ratings por item,
seis dimensões e sampling estratificado. A configuração é deliberadamente
`PREPARED_NOT_STARTED`; nenhum job de produção é criado automaticamente.

## QA e dataset

As tabelas preservam eventos, timing, skips, labels brutos, diagnósticos,
adjudicações e versões de dataset. A rota de métricas fornece média, mediana,
IQR, timing e contagens por dimensão e sinaliza `INSUFFICIENT_SAMPLE` até haver
coleta suficiente. A fila de adjudicação é determinística quando a amplitude
ordinal chega a dois pontos. A versão publicada contém manifesto, labels brutos
e adjudicações separadas e é imutável.

Para fechar os gates de validação ainda é necessário coletar 100 itens reais,
calcular mediana/IQR, disagreement rate, alpha ordinal quando houver amostra,
comparar humano × modelo e revisar o dataset antes de qualquer treinamento.

## Operação

A migration é aditiva (`V13__excerpt_quality_learning_v1.sql`). Não edite
migrations anteriores. O admin pode criar e iniciar uma campanha explicitamente;
o pipeline não habilita bulk nem chama LLM comercial. As ações do admin exibem
erros de API ao operador; o sampling usa conflito idempotente por
`(campaign_id, excerpt_id)` e as métricas qualificam `created_at` pela anotação
para evitar falhas quando os joins possuem timestamps homônimos.
