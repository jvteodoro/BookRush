# Persistência de experimentos do feed

`V8__viewable_repeat_lookup_index.sql` adiciona índice parcial para buscar
impressões viewable anteriores pelo livro, instante de viewability e ID. Ele
suporta a projeção de repetição em janela móvel de 30 dias.

`V9__candidate_stage_counts.sql` armazena no request somente as contagens de
candidatos recuperados no pool limitado da requisição, candidatos com score de
analytics e candidatos selecionados pelo ranking. A primeira contagem não é o
total de livros elegíveis no catálogo: a recuperação é limitada e o catálogo
não fornece nesse contrato um denominador com as regras completas de elegibilidade
do feed. IDs individuais não são adicionados ao ledger.

O contexto JSON de cada request também contém buckets categóricos de confiança
e tamanho de perfil, cobertura de fadiga do conjunto candidato e cold-start.
O reporting consome esses buckets para gerar a série FD-12 sem exportar valores
individuais.

`V7__impression_viewable_timestamp.sql` adiciona `viewable_at` ao ledger de
impressões. O endpoint de viewability grava somente o primeiro instante em que
o card cruza o limiar visível; repetições são idempotentes. Isso permite medir
coortes maduras e a janela de atribuição desde a viewability real, sem inferir
o horário a partir da criação da impressão.

`V6__persistent_feed_experiment_assignments.sql` cria a tabela proprietária
`recommendation.experiment_assignment`. A chave `(experiment_id, subject_key)`
garante assignment estável; `variant` só aceita `CONTROL` ou `TREATMENT` e
`assignment_version` identifica a regra que produziu a atribuição.

O experimento é opt-in por `FEED_EXPERIMENT_ENABLED=false`. O serviço grava a
linha antes de persistir o request do feed e reutiliza a variante existente em
requests seguintes. A tabela não recebe texto de livro, payload de usuário ou
vetor; `subject_key` permanece restrito ao bounded context de recommendation e
não é exportado pelos dashboards. O reporting consome somente agregados
`reporting.experiment_daily`.
