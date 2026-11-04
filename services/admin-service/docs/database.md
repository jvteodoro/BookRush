# Banco

Flyway cria o schema `admin` e a tabela append-only `audit_log`. O serviço é o único escritor desse schema.

A migration V5 também cria o schema de leitura `reporting`, mantido pelo
`ReportingProjection` do admin-service. Ele materializa somente agregados
versionados a partir da fronteira de eventos do behavior-service; não expõe
payloads, subjects ou IDs de entidades na API. Enquanto a população dos eventos
não estiver classificada, os snapshots usam `UNCLASSIFIED` e a API responde
`UNTRUSTED`, nunca `PRODUCT` ou zero implícito.
