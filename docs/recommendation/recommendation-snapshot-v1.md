# Recommendation analytics snapshot V1

O `book-analytics-service` expõe o contrato interno S2S
`POST /api/internal/v1/content-analytics/recommendation-snapshot`. O endpoint é
protegido pelo mesmo audience `bookrush-analytics` e scope
`bookrush.analytics` usados por `book-scores`; não é uma API de browser e não
possui CORS público.

## Semântica

- A entrada aceita no máximo 100 IDs distintos, com `offset` e `limit` entre 1
  e 100.
- A versão textual é obtida exclusivamente de
  `catalog.book_asset.current_version_id`; versões anteriores não são
  consideradas mesmo que ainda tenham excerpts ou embeddings.
- O score é o maior score elegível da versão atual, usando o rank não nulo mais
  recente de cada excerpt. A resposta preserva `rankerCode`, `rankerVersion` e
  `rankConfigurationHash`.
- O embedding documental é associado somente quando o modelo configurado
  (`bge-m3-dense-v1` por padrão) possui versão, dimensão, hash e localização de
  objeto coerentes. Quando não há embedding, modelo compatível ou versão atual,
  `embedding` é `null`; zero-vector nunca é fabricado.
- A resposta retorna metadados e linhagem do vetor (`sourceAssetVersionId`,
  modelo, versão, dimensão, hash, storage, bucket e object key). O vetor bruto
  continua em `books-ml`, portanto uma leitura do feed não faz download de um
  objeto por livro.

## Operação e rollout

O endpoint usa uma consulta parametrizada e limitada. A seleção do modelo pode
ser alterada com `ANALYTICS_RECOMMENDATION_EMBEDDING_MODEL` e, opcionalmente,
`ANALYTICS_RECOMMENDATION_EMBEDDING_MODEL_VERSION`. O padrão do score continua
`ANALYTICS_BOOK_SCORES_STRATEGY=CURRENT` e `ANALYTICS_BOOK_SCORES_JIT=DEFAULT`.

O contrato mede duração e quantidade solicitada/retornada em métricas de baixa
cardinalidade:

- `bookrush.analytics.recommendation_snapshot.endpoint`
- `bookrush.analytics.recommendation_snapshot.requested`
- `bookrush.analytics.recommendation_snapshot.returned`

O consumo pelo recommendation-service deve ser introduzido em uma etapa
posterior com cache por versão de asset/modelo. Essa projeção foi implementada
no `AnalyticsScoreClient`: o refresh é feito em lote, a troca do mapa é
atômica, e cada entrada é indexada logicamente por `bookId`,
`sourceAssetVersionId` e modelo do embedding. Uma nova versão remove a entrada
anterior daquele livro antes da publicação da nova. Até lá, o endpoint é uma
fonte S2S versionada e auditável, sem alterar o caminho atual do feed.

Quando o refresh expira e o analytics fica indisponível, o último snapshot
válido é usado somente como fallback stale; nenhum vetor zero ou score
inventado é criado. As métricas `bookrush.feed.book_features.cache` e
`bookrush.feed.book_features` mostram hit/miss, stale-on-error, cobertura e
quantidade de embeddings. O TTL usa a configuração já existente
`FEED_ANALYTICS_RANKING_CACHE_TTL`.

## Validação

`RecommendationSnapshotQueryRepositoryIT` usa PostgreSQL/Testcontainers e
verifica troca de versão atual, seleção de score/ranker da versão correta,
deduplicação e ausência explícita quando o livro ou embedding não existe.
Execute com:

```bash
RUN_SQL_INTEGRATION=true TESTCONTAINERS_DOCKER_API_VERSION=1.40 \
  mvn -q -Dtest=RecommendationSnapshotQueryRepositoryIT test
```
