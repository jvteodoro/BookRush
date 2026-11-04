# Persistência de experimentos do feed

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
