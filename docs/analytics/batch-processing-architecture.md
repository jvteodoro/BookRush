# Arquitetura de processamento em lote do Content Analytics

**Escopo:** job persistente de `book-analytics-service` para processar versões
textuais de livros em lotes grandes, com retomada após falhas e sem reduzir as
garantias de durabilidade do PostgreSQL.

**Estado da implementação:** as otimizações descritas abaixo estão publicadas
na branch `feature/close-small-beads`. O piloto de produção ainda é uma
execução em andamento; este documento não transforma uma execução incompleta
em um gate de capacidade aprovado.

## 1. Problema que motivou as mudanças

O piloto Gutenberg foi iniciado com centenas de itens persistidos na fila.
Depois de reiniciar o container, alguns itens permaneceram em `RUNNING`,
impedindo o job de alcançar um estado terminal. Quando os itens começaram a
ser processados, a análise também mostrou dois custos dominantes:

- cada feature de cada excerpt gerava uma escrita JDBC individual, aumentando a
  quantidade de commits/WAL e deixando a thread de agendamento em `WALSync`;
- a poda de candidatos comparava um candidato com todos os anteriores, criando
  custo quadrático para livros com muitos excerpts.

O objetivo foi tornar a fila resumível, limitar o custo por item e preservar a
semântica dos resultados. Não foram desativados `fsync` ou
`synchronous_commit`, não houve alteração de migration e não foi introduzido
processamento automático de novos livros no startup.

## 2. Fluxo de lote

```text
analysis_job / analysis_job_item
          |
          v
  recuperar leases antigas --------------------+
          |                                     |
          v                                     |
      claim PENDING                             |
          |                                     |
          v                                     |
  ler asset imutável + validar hash/UTF-8       |
          |                                     |
          v                                     |
  capítulos -> janelas de sentença             |
          |                                     |
          v                                     |
  filtros baratos + poda de duplicatas         |
          |                                     |
          v                                     |
  runtime linguístico por excerpt               |
          |                                     |
          v                                     |
  batchUpdate de features ---------------------+
          |
          v
  embeddings/NLI/ranking opcionais
          |
          v
  analysis_run + item COMPLETED/FAILED
```

O banco controla a fila e a linhagem; o conteúdo grande permanece no storage
compatível com S3. O worker lê o objeto fora de uma transação longa e grava
somente metadados, excerpts e observações no schema `analytics`. Isso evita
manter uma transação aberta durante download, parsing ou chamadas HTTP.

## 3. Recuperação de leases após restart

Antes, um processo morto podia deixar um item em `RUNNING` indefinidamente.
O worker agora executa uma recuperação antes de buscar trabalho:

- considera somente itens `RUNNING` cuja `started_at` excede
  `ANALYTICS_WORKER_STALE_AFTER_SECONDS`;
- limita a recuperação a jobs `PENDING` ou `RUNNING` com
  `cancel_requested=false`;
- devolve o item a `PENDING`, limpa timestamps de execução e remove a
  associação transitória de `analysis_run`;
- deixa o próximo claim incrementar `attempt_count`, preservando o histórico
  de tentativas no próprio item.

O padrão é 900 segundos. O valor precisa ser maior que o pior tempo legítimo de
uma análise; um valor baixo pode reencaminhar trabalho ainda ativo. A operação
não remove jobs, objetos, execuções históricas ou dados do catálogo. Jobs
cancelados não são reativados.

Essa estratégia é uma lease lógica, não um heartbeat. Ela é adequada porque o
processamento de um item é limitado por timeout de storage/runtime e porque o
estado persistente permite retomar o lote sem intervenção manual no banco.

Falhas transitórias durante o processamento seguem o mesmo princípio: o item
volta a `PENDING`, preserva `attempt_count` e aguarda um backoff progressivo.
São permitidas três tentativas por padrão. Falhas determinísticas, como hash
ou tamanho incompatível e versão de asset indisponível, permanecem terminais.
Assim, a retomada automática consegue terminar um lote interrompido sem
transformar um erro permanente em loop infinito.

## 4. Persistência em lote e durabilidade

`persistExcerpt` continua inserindo ou reutilizando o excerpt de forma
idempotente. Depois de calcular as features, o worker monta todas as linhas do
excerpt e usa um único `JdbcTemplate.batchUpdate`, em vez de executar um
`jdbc.update` para cada definição de feature.

O ganho arquitetural é reduzir round-trips, commits e sincronizações WAL por
excerpt. Isso diminui a pressão no caminho de escrita sem alterar a política de
durabilidade. Em produção permanecem habilitados:

- `fsync=on`;
- `synchronous_commit=on`;
- `full_page_writes=on`.

Um `WALSync` prolongado deve ser investigado com `pg_stat_activity`,
`pg_stat_wal`, `pg_stat_bgwriter`, latência do volume e tamanho do lote. Não é
um motivo para desligar durabilidade globalmente. A tabela
`behavior.outbox` também pode disputar o mesmo caminho de WAL e deve ser
considerada no diagnóstico.

## 5. Poda de candidatos sem comparação quadrática

As regras de negócio foram preservadas:

- duplicata exata continua sendo identificada pela forma normalizada;
- near-duplicate continua exigindo razão de tamanho mínima e Jaccard `>= 0,85`;
- texto vazio, curto, longo e boilerplate continua sendo rejeitado com a mesma
  razão auditável;
- a versão do gerador permanece registrada no resultado.

A implementação anterior comparava cada candidato com todos os conjuntos de
   palavras aceitos. A implementação atual mantém um índice invertido
`palavra -> conjuntos de palavras` e só avalia candidatos que compartilham
alguma palavra. Para não re-hashar conjuntos grandes durante a coleta dos
candidatos, a coleção transitória usa identidade de objeto
(`IdentityHashMap`); isso não muda o resultado do Jaccard, apenas evita custo
de hash estrutural repetido.

O custo deixa de ser necessariamente `O(n²)` e passa a depender do número de
candidatos que compartilham termos. Palavras muito comuns ainda podem gerar
uma lista grande, portanto o índice é uma redução de trabalho, não uma
garantia de tempo linear para qualquer corpus.

## 6. Chamadas ao runtime e limite atual

O worker usa timeout configurável para o runtime linguístico e trata respostas
de modelo indisponível como estado explícito, nunca como zero silencioso. O
endpoint de linguagem pode rejeitar um documento muito grande com `413`; esse
caso é registrado como indisponibilidade do modelo e o restante das features
determinísticas pode continuar.

Após as otimizações de banco e poda, o próximo gargalo observado no piloto foi
a chamada serial `LinguisticRuntimeClient.analyze` por excerpt. O runtime
respondeu com `200` e consumiu CPU, portanto esse comportamento não é uma
falha de lease ou de WAL. Ainda não foi implementada concorrência ilimitada:
paralelizar sem limite poderia saturar CPU, aumentar timeouts e causar uma
nova tempestade de retries.

O próximo passo de capacidade deve ser uma janela de concorrência limitada,
com back-pressure, circuit breaker e métricas por lote; alternativamente, um
endpoint de análise em lote no runtime. Essa pendência está explicitamente
separada das correções já entregues.

## 7. Configuração relevante

| Variável | Padrão | Função |
| --- | ---: | --- |
| `ANALYTICS_WORKER_ENABLED` | `false` no app / habilitado no Compose | Liga o consumidor persistente. |
| `ANALYTICS_WORKER_POLL_DELAY_MS` | `1000` | Intervalo entre ciclos sem trabalho. |
| `ANALYTICS_WORKER_STALE_AFTER_SECONDS` | `900` | Limite para recuperar uma lease órfã. |
| `ANALYTICS_WORKER_MAX_ATTEMPTS` | `3` | Máximo de claims por item antes de `FAILED`. |
| `ANALYTICS_WORKER_RETRY_DELAY_SECONDS` | `30` | Backoff base entre retries transitórios. |
| `ANALYTICS_RUNTIME_TIMEOUT_SECONDS` | `10` no app / `60` no Compose | Timeout de chamadas ao runtime. |
| `ANALYTICS_MAX_EXCERPTS_PER_ITEM` | `500` | Limite por item no piloto; a geração/poda também recebe no máximo 3× esse limite para impedir custo quadrático em obras longas. Aumentar somente após o gate de capacidade. |
| `ANALYTICS_SCOPE_EMBEDDINGS_ENABLED` | conforme ambiente | Habilita embeddings somente quando o artefato está preparado. |
| `ANALYTICS_NLI_ENABLED` | `false` | Mantém NLI desligado por padrão. |

O valor efetivo do ambiente deve ser verificado no Compose/container; o valor
do `application.yml` sozinho não prova a configuração de produção.

## 8. Observabilidade e operação segura

Para diagnosticar um lote, use primeiro a API administrativa e depois o banco
somente para diagnóstico:

```text
GET /api/admin/v1/analytics/jobs/{jobId}
GET /api/admin/v1/analytics/jobs/{jobId}/items
POST /api/admin/v1/analytics/jobs/{jobId}/resume
```

No PostgreSQL, observar contagens por estado, idade de `started_at`,
`pg_stat_activity`, `pg_stat_wal` e `pg_stat_bgwriter`. Não alterar status
manualmente para “destravar” um job. Corrigir a causa, aguardar a lease ou
usar `resume` quando a API permitir.

O host/container deve ser conferido junto com o banco: imagem efetiva,
timestamp de criação, healthcheck, logs e configuração efetiva. Um `200` de
`/api/status` confirma saúde do gateway/catalog, mas não confirma que o lote
terminou; o gate do piloto exige estado terminal e contagens coerentes do job.

## 9. Evidências das entregas

| Commit | Mudança | Evidência |
| --- | --- | --- |
| `188b5be` | Recuperação de itens `RUNNING` órfãos e configuração de lease. | Jenkins analytics `#29`, container healthy. |
| `fdc9231` | Persistência das features com `batchUpdate`. | Jenkins analytics `#30`, testes Maven passaram. |
| `4ade298` | Índice invertido para poda de near-duplicates. | Jenkins analytics `#31`, container healthy. |
| `bc59f89` | Correção da coleção de identidade para evitar hash estrutural repetido. | Jenkins analytics `#32`, container healthy. |

Validações locais da sequência: `mvn -q -f
services/book-analytics-service/pom.xml test` passou e `git diff --check`
passou. O smoke público de `/api/status` retornou `200`. A última observação
do piloto foi `RUNNING`, com itens ainda pendentes/ativos; portanto o piloto de
500 livros permanece aberto e não deve ser reportado como concluído.

## 10. Critério para declarar o piloto pronto

O processamento em lote só deve ser promovido a gate de capacidade quando:

1. o job chegar a `COMPLETED` ou falhar com causa terminal conhecida;
2. não houver itens presos em `RUNNING` e as contagens persistidas fecharem;
3. throughput, latência por estágio, WAL, CPU/memória do runtime, storage e
   taxa de `MODEL_UNAVAILABLE` forem coletados;
4. a redução de candidatos e amostras de qualidade editorial forem revisadas;
5. o relatório registrar claramente o que foi medido em produção, o que foi
   executado offline e quais modelos estavam habilitados.

Esse critério separa confiabilidade operacional, desempenho e qualidade do
modelo. Um lote que termina não é automaticamente um lote editorialmente
aprovado.
