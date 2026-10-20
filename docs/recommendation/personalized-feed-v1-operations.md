# Personalized Feed V1 — operação, replay e handoff

## Estado e fallback

O caminho personalizado é controlado por `FEED_PERSONALIZED_MODE`:

* `OFF` (padrão): mantém `analytics-aware-v1`/heurística existente.
* `SHADOW`: calcula `PERSONALIZED_V1`, mas entrega a ordenação do controle.
* `ON`: entrega o ranking personalizado apenas quando behavior, snapshot,
  cobertura e confiança passam o gate.

`FEED_PERSONALIZED_ROLLOUT_PERCENTAGE` controla um bucket determinístico por
subject para ativação gradual. O rollback operacional é definir `MODE=OFF` e
reiniciar somente o recommendation-service; não requer migration rollback nem
remove dados do ledger.

## Observabilidade

O serviço publica timers em `bookrush.feed.stage.duration` e contadores de baixa
cardinalidade para caminho, razão de fallback, confiança em buckets, tamanho de
perfil, cobertura do shadow e resultado do feed. Subjects, IDs de livros, texto,
tokens e vetores não são tags nem aparecem nos logs.

O ledger guarda `candidateSetHash`, caminho/razão, versões e decomposição de
score para auditoria. `PersonalizedDecisionReplay` calcula uma impressão
determinística da ordenação e preserva os hashes de configuração para comparar
uma fixture sem reprocessar PII.

## Diagnóstico

1. Verifique `bookrush.feed.requests{outcome}` e os timers por `stage`.
2. Em `SHADOW`, compare `bookrush.feed.personalized.shadow.overlap` com a
   população `compared`; divergência alta não é, sozinha, motivo para ativar.
3. Investigue razões `behavior-unavailable`, `snapshot-unavailable`,
   `coverage-low`, `confidence-low` e `rollout-excluded` antes de alterar o
   percentual.
4. Se houver erro/latência, volte para `OFF`; o feed deve continuar servindo o
   caminho seguro.

## Dataset para LTR futuro

`services/recommendation-service/scripts/export-pfv1-dataset.sh` exporta o
contrato `recommendation-pfv1-dataset-v1` a partir de um snapshot somente-leitura.
Cada linha liga `recommendation_item_id` à impressão, decisão, posição, score e
componentes persistidos. O cutoff é obrigatório; eventos posteriores são
excluídos para evitar leakage. O arquivo deve ser acompanhado de SHA-256, número
de linhas, cutoff, commit e versão do schema. O export não treina nem promove
LTR e é destinado ao bead downstream `bookrush-iv0y`.

## Rollout e evidência

Antes de ativar `ON`, executar os testes da branch, `SHADOW` com uma amostra
controlada e o smoke autenticado do gateway. Registrar commit, imagem/digest,
configuração efetiva, população e horários. O status de produção só pode ser
`GO` após pipeline Jenkins e smoke da revisão exata; sem isso permanece `NO-GO`.
