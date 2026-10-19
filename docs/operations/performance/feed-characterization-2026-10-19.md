# Caracterização de performance do feed — 2026-10-19

## Objetivo

Medir o caminho autenticado do feed e separar os efeitos de tamanho do lote,
concorrência e carga do processamento analítico. O estudo foi executado em
produção, sem alterar dados de catálogo ou interromper permanentemente o lote
de analytics.

## Arquitetura medida

```text
cliente
  -> gateway público /api/v1/reader/feed
  -> reader-bff-service (timeout upstream: 15 s)
  -> recommendation-service
       -> catalog-service: até min(size * 3, 100) candidatos
       -> book-analytics-service: scores de até 100 livros
       -> filtro fail-closed de livros sem score
       -> excerpts em paralelo (máx. 8, orçamento 900 ms)
       -> persistência de request/items/impressions
```

Para `size=50`, uma chamada consulta 100 candidatos no catálogo e envia até
100 IDs ao endpoint de scores. O ranking analítico possui cache de 10 minutos;
por isso o benchmark público representa o comportamento do feed com cache
operacional, enquanto a análise SQL abaixo mede o caminho de scores diretamente.

## Metodologia

Harness reproduzível: [`scripts/benchmark-feed-performance.sh`](../../../scripts/benchmark-feed-performance.sh).

Configuração desta execução:

- autenticação Authorization Code + PKCE com usuário de teste do realm
  `bookrush`; tokens não foram gravados nos artefatos;
- 12 chamadas sequenciais para cada tamanho: `10`, `25` e `50`;
- 16 chamadas `size=50` em quatro grupos concorrentes de quatro clientes;
- 12 chamadas `size=50` com `bookrush-book-analytics-runtime-1` pausado;
- o worker foi reativado por `trap` e confirmado como `running` antes do fim;
- cada chamada registrou status, duração de parede, quantidade de itens e
  quantidade de excerpts anexados;
- intervalos de confiança de 95% calculados por bootstrap com 20.000
  reamostragens, semente fixa 7;
- foram coletadas amostras de CPU/memória/I/O dos containers antes, durante a
  pausa e após a retomada.

Artefatos brutos locais, ignorados pelo Git:

`docs/operations/incidents/evidence/raw/feed-benchmark-20261019T005017Z/`

- `requests.tsv`: 64 observações individuais;
- `container-stats.tsv`: snapshots de CPU, memória, rede e block I/O.

## Resultados

Todas as 64 chamadas retornaram HTTP 200. O número de itens foi estável por
cenário: 2 para `size=10`, 10 para `size=25` e 18 para `size=50`; os itens
retornados tinham excerpts anexados.

| Cenário | n | Mediana | IC95% mediana | Média | IC95% média | Desvio | P95 | Erros |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Sequencial, size 10, worker ativo | 12 | 608 ms | 596–648 ms | 621 ms | 603–639 ms | 33 ms | 674 ms | 0 |
| Sequencial, size 25, worker ativo | 12 | 725 ms | 712–748 ms | 736 ms | 692–784 ms | 84 ms | 884 ms | 0 |
| Sequencial, size 50, worker ativo | 12 | 905 ms | 867–924 ms | 906 ms | 884–931 ms | 43 ms | 975 ms | 0 |
| Concorrente, 4× size 50, worker ativo | 16 | 1.390 ms | 1.322–1.445 ms | 1.399 ms | 1.347–1.455 ms | 116 ms | 1.594 ms | 0 |
| Sequencial, size 50, worker pausado | 12 | 709 ms | 693–727 ms | 725 ms | 702–755 ms | 50 ms | 819 ms | 0 |

Comparações:

- aumentar o tamanho de 10 para 50 elevou a mediana de 608 ms para 905 ms
  (+48,8%);
- quatro requisições concorrentes elevaram a mediana de 905 ms para 1.390 ms
  (+53,7%) e o desvio padrão de 43 ms para 116 ms;
- pausar o worker reduziu a mediana de 905 ms para 709 ms (-21,6%, ou 195,5
  ms), confirmando contenção de CPU/I/O relevante, embora não seja a única
  causa;
- a execução anterior com timeout de 700 ms explica o feed vazio: o caminho
  de `size=50` excedia o limite antes de conseguir retornar os scores.

### Carga dos containers

| Momento | Analytics runtime | Analytics service | PostgreSQL |
| --- | ---: | ---: | ---: |
| Antes | 566,66% CPU / 3,048 GiB | 1,58% CPU / 468,4 MiB | 0,87% CPU / 296,8 MiB |
| Worker pausado | 0,00% CPU / 3,024 GiB | 0,82% CPU / 468,2 MiB | 1,38% CPU / 309,8 MiB |
| Após retomada | 566,90% CPU / 3,064 GiB | 0,08% CPU / 468,0 MiB | 0,93% CPU / 299,6 MiB |

O runtime estava executando embeddings (`POST /v1/embeddings`) e consumindo
aproximadamente 5,7 CPUs. O PostgreSQL não estava saturado em conexões ou
locks no diagnóstico: 18 sessões, 17 ociosas, uma ativa e zero locks não
concedidos.

## Gargalos caracterizados

### 1. Consulta de scores analíticos — confirmado

`EXPLAIN ANALYZE` da consulta de 100 livros mediu aproximadamente 1.011 s com
JIT padrão habilitado, mesmo sem usuários concorrentes. O plano mostrou:

- 435,547 ms somente em compilação JIT do PostgreSQL;
- sequential scan de 492.366 excerpts elegíveis;
- 69.695 buscas laterais em `excerpt_rank`;
- sort externo em disco de 3.104 KiB;
- 21 livros retornados.

#### Experimento JIT on/off, repetido e sob concorrência

Foi executado o mesmo `EXPLAIN (ANALYZE, BUFFERS, SETTINGS)` em 10 pares
sequenciais no mesmo banco, alternando a ordem (`on/off`, depois `off/on`),
com o mesmo conjunto de 100 livros e dentro de transações revertidas. Depois
foram feitas 5 rodadas com 4 sessões simultâneas para cada configuração. A
única variável foi `SET LOCAL jit`; nenhuma configuração persistente foi
alterada e o worker de analytics permaneceu em execução.

| Cenário | n | Mediana | IC95% mediana | Média | P95 | JIT mediano |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Sequencial, JIT on | 10 | 911,821 ms | 893,045–953,907 ms | 939,855 ms | 1.171,437 ms | 412,833 ms |
| Sequencial, JIT off | 10 | 554,275 ms | 539,755–570,660 ms | 560,491 ms | 641,954 ms | 0 ms |
| 4 sessões simultâneas, JIT on | 20 | 1.010,388 ms | 986,593–1.030,156 ms | 1.005,432 ms | 1.070,337 ms | 402,265 ms |
| 4 sessões simultâneas, JIT off | 20 | 651,738 ms | 625,678–664,144 ms | 642,524 ms | 689,540 ms | 0 ms |

Nos pares sequenciais, desligar JIT reduziu a mediana em 357,546 ms (39,21%);
a diferença pareada `on - off` teve mediana de 364,401 ms e IC95% de
314,076–403,979 ms. Nas sessões simultâneas, desligar JIT reduziu a mediana em
358,650 ms (35,50%). Portanto, o paralelismo não tornou JIT vantajoso: o custo
de compilação continuou presente por sessão, com JIT mediano de cerca de
402 ms, e JIT off permaneceu mais rápido.

O paralelismo elevou a mediana em relação ao sequencial em 10,8% com JIT on
e 17,6% com JIT off. Isso indica contenção adicional entre sessões, mas não
compensa os aproximadamente 400 ms de compilação JIT. Todas as execuções
retornaram 21 livros e mantiveram o mesmo plano lógico/cardinalidades.

Como o plano, cardinalidades e operações de I/O permaneceram essencialmente
iguais, o ganho observado é atribuível principalmente ao custo de compilação
JIT, não a uma mudança no algoritmo da consulta. O resultado é estatisticamente
consistente nesta amostra, mas ainda recomenda testar a alteração no ambiente
do serviço antes de mudar a configuração global do PostgreSQL.

O harness reproduzível está em
`scripts/benchmark-score-jit.sh`. Os dumps completos, sem credenciais, estão
em:

- `docs/operations/incidents/evidence/raw/feed-benchmark-20261019T005017Z/explain-score-jit-on-off.txt`;
- `docs/operations/incidents/evidence/raw/feed-benchmark-20261019T063433Z/jit-repeat/`;
- a execução anterior corrigida, mantida localmente, em
  `docs/operations/incidents/evidence/raw/feed-benchmark-20261019T063332Z/jit-repeat/`.

#### Tentativa de validação no endpoint completo

Foi feita uma tentativa controlada de trocar somente o container de
`book-analytics-service` por uma instância equivalente com `jit=off` no
parâmetro JDBC, renovando também o `recommendation-service` para resolver o
alias Docker. O smoke público respondeu HTTP 200, porém com `items=[]`; por
isso esses tempos não foram usados como comparação de JIT.

A validação direta após restaurar o container mostrou a causa independente:
o container atualmente em produção está saudável, mas a imagem
`local/bookrush/book-analytics-service:173-9373f7a` retorna HTTP 404 para
`/api/internal/v1/content-analytics/book-scores`, embora esse contrato exista
na revisão local `ab450f7`. O endpoint ausente faz o recommendation-service
falhar fechado e explica o feed vazio. Portanto, antes do próximo teste de
JIT no endpoint completo, é necessário publicar/deployar uma imagem de
analytics que contenha o endpoint e repetir o smoke com itens reais.

Os artefatos da tentativa, todos locais e ignorados pelo Git, estão em:

`docs/operations/incidents/evidence/raw/feed-benchmark-20261019T063433Z/endpoint-jit-off/`

`docs/operations/incidents/evidence/raw/feed-benchmark-20261019T063433Z/endpoint-jit-off-renewed/`

`docs/operations/incidents/evidence/raw/feed-benchmark-20261019T063433Z/endpoint-restore-smoke/`

Esse custo é estrutural e explica por que 700 ms era insuficiente. O timeout de
2 s é uma mitigação operacional, não a solução definitiva.

Mitigações candidatas, a testar isoladamente:

1. índice/projeção para o último rank elegível por `excerpt_id`;
2. materialização de score por livro/versão de asset;
3. substituir a varredura global por uma relação de excerpts somente das
   versões atuais solicitadas;
4. repetir o experimento com JIT desligado e medir custo/variância antes de
   decidir uma configuração específica da consulta;
5. cache explícito de scores por versão de asset, com invalidação idempotente.

### 2. Contenção do worker de embeddings — confirmado

Com o worker ativo, a chamada sequencial `size=50` foi 27,6% mais lenta que com
o worker pausado. A CPU do runtime caiu de 566,66% para 0% durante a pausa e
retornou a 566,90% após retomada. Isso demonstra interferência de recursos,
mas não um bloqueio de banco: recommendation e analytics-service ficaram com
CPU baixa e não havia locks aguardando.

Mitigações candidatas:

- reservar CPU/GPU e I/O para requests síncronos do feed;
- limitar concorrência do worker durante horário interativo;
- separar o runtime de embeddings em host/worker dedicado;
- aplicar fila com backpressure e prioridade para tarefas online;
- medir afinidade de CPU, throughput GPU e latência do banco antes de escolher
  limites definitivos.

### 3. Concorrência de usuários — confirmado como amplificador

Quatro clientes simultâneos aumentaram a mediana em 53,7% e o p95 observado
foi 1.594 ms. Portanto, múltiplos usuários podem degradar o feed, mas o
problema não é uma saturação clássica de conexões: o banco tinha ampla margem
de conexões e nenhum lock pendente. O efeito provável é a combinação de:

- consultas pesadas concorrentes;
- CPU/I/O compartilhados com embeddings;
- escritas síncronas do ledger de recomendações;
- enriquecimento paralelo por request.

### 4. Política fail-closed e timeout — confirmado como impacto funcional

`AnalyticsScoreClient` retorna fallback vazio quando o lote excede o timeout.
Com o ranking analítico habilitado, `RecommendationController` elimina livros
sem score. Assim, uma falha de latência vira `items=[]`, mesmo que o catálogo
e os excerpts existam. O timeout de 2 s evita a falha observada para a amostra
atual, mas não impede regressão com carga maior.

Mitigações candidatas: circuit breaker explícito, cache stale-while-revalidate,
limite adaptativo de candidatos e resposta degradada observável, sem exibir
livros sem analytics quando a regra de produto exigir fail-closed.

## Gargalos ainda não isolados

1. **Catálogo:** o feed busca até 100 livros por chamada; falta medir o tempo
   isolado do `GET /api/v1/books` no mesmo conjunto de IDs.
2. **Enriquecimento de excerpts:** há executor de 8 threads e orçamento de
   900 ms; o benchmark público mostrou excerpts anexados, mas não separou o
   tempo do lote de excerpts do restante do recommendation-service.
3. **Persistência do ledger:** cada item gera inserts de item e impression;
   ainda falta medir a contribuição dessas escritas com tracing/timers.
4. **Cache frio versus quente:** o cache de scores dura 10 minutos; esta
   bateria não reiniciou recommendation-service nem invalidou cache, para não
   interromper usuários. É necessário um ambiente de carga isolado ou uma
   métrica explícita de hit/miss por request.
5. **Gateway/TLS:** o tempo público inclui proxy, TLS e BFF. O BFF possui
   orçamento de 15 s, mas a decomposição direta deve ser coletada em uma
   bateria posterior.

## Plano de experimentos seguinte

Executar uma hipótese por vez, preservando o mesmo corpus e amostra:

1. **Concluído:** repetir JIT on/off em 10 pares e em sessões simultâneas;
2. criar índice/projeção em banco descartável e repetir `EXPLAIN ANALYZE`;
3. medir score materializado por versão de asset;
4. separar worker de embeddings e repetir a matriz de concorrência;
5. medir cache frio/quente com métricas de hit/miss;
6. medir catálogo, scores, excerpts e persistência com timers Micrometer;
7. repetir cargas de 1, 4 e 8 clientes e validar p95/p99 e taxa de erro;
8. publicar/deployar a imagem de analytics que contém o contrato de scores e
   validar HTTP 200 com itens reais;
9. testar `jit=off` na sessão do serviço em ambiente controlado e comparar o
   endpoint completo antes de alterar produção;
10. somente depois ajustar timeout, concorrência, JIT ou circuit breaker em
   produção.

## Conclusão operacional

### Correção do bloqueio de imagem stale — 2026-10-19

O teste do endpoint completo revelou que a amostra anterior não media o
ranking: o container efetivo estava em
`local/bookrush/book-analytics-service:173-9373f7a`, sem a rota
`POST /api/internal/v1/content-analytics/book-scores`, que respondia `404`.
O código da revisão `0b13579` já continha o controller e o contrato OpenAPI,
mas a imagem antiga ainda estava implantada.

Foi publicada a branch `feature/analytics-small-batches` e executado o job
`bookrush-microservice-book-analytics-service #62` com `DEPLOY=true` e
`DEPLOY_TARGET=compose`, autenticado como `codex-jenkins-agent`. O build
terminou `SUCCESS` em 56,374 ms. O container efetivo passou a usar
`bookrush/book-analytics-service:local`, com digest
`sha256:9007082a0283283226a879b2e45501c8a280aa6faab56728c6475b8376ac92d8`,
estado `running` e healthcheck `healthy`; o Flyway validou as 27 migrations.

Smoke pós-deploy, sem registrar credenciais:

- chamada S2S sem bearer: `401`;
- chamada S2S com token novo e cinco livros elegíveis: `200`, quatro scores
  retornados;
- feed público sem token: `401`;
- feed público com login OIDC + PKCE: `200`, `rankingVersion=analytics-aware-v1`
  e item retornado.

O worker `bookrush-book-analytics-runtime-1` permaneceu `running` e
`paused=false`. Portanto, o diagnóstico anterior de feed vazio por rota
ausente foi corrigido; a bateria de latência anterior deve ser repetida agora
com o endpoint real antes de comparar novas otimizações.

Repetição automatizada pós-deploy, com 32 chamadas e dumps locais em
`docs/operations/incidents/evidence/raw/feed-benchmark-20261019T065250Z/`:

- sequencial: size 10, 25 e 50 tiveram 5/5 respostas HTTP 200, médias de
  661,4 ms, 847,6 ms e 1.093,8 ms, respectivamente;
- concorrência: 12/12 chamadas simultâneas size 50 retornaram HTTP 200, média
  de 1.391,0 ms e 252 itens agregados;
- worker pausado: 5/5 chamadas size 50 retornaram HTTP 200, média de 783,8 ms;
- o worker foi retomado pelo cleanup e terminou `running paused=false`.

Esta execução confirma o endpoint completo funcional após a troca da imagem;
os tempos são uma caracterização de validação com 5 repetições, não substituem
a bateria estatística maior já documentada.

O feed está funcional com o timeout de 2 s e a bateria teve 0% de erros. A
consulta de scores é o gargalo dominante; o worker de embeddings e a
concorrência de usuários amplificam a latência. O job dos 500 livros foi
reativado e confirmado em execução antes do encerramento. O próximo trabalho
deve otimizar a consulta/projeção de scores e adicionar timers por fase antes
de aumentar novamente o volume de candidatos ou a concorrência do worker.
