# Banco

Flyway cria o schema `admin` e a tabela append-only `audit_log`. O serviço é o único escritor desse schema.

A migration V5 também cria o schema de leitura `reporting`, mantido pelo
`ReportingProjection` do admin-service. Ele materializa somente agregados
versionados a partir da fronteira de eventos do behavior-service; não expõe
payloads, subjects ou IDs de entidades na API. Enquanto a população dos eventos
não estiver classificada, os snapshots usam `UNCLASSIFIED` e a API responde
`UNTRUSTED`, nunca `PRODUCT` ou zero implícito.

A migration V6 adiciona `reporting.feed_daily`, derivado do ledger de
recomendações e de `behavior.outcome_attribution`, e
`reporting.pipeline_snapshot`, derivado dos estados de jobs e fases do
analytics. Ambos são reprocessáveis por janela e preservam `calculation_version`
e `source_watermark`. Dwell permanece nulo até existir fato validado; o painel
deve mostrá-lo como `NOT_INSTRUMENTED`.

A V7 adiciona `reporting.attribution_daily` e
`reporting.reconciliation_daily`. A primeira conserva a classificação explícita
dos outcomes; a segunda compara IDs de impressão do ledger com a telemetria de
viewability e calcula duplicidades sem apagar o histórico bruto.
