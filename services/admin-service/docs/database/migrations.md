# Migrations do admin-service

As migrations Flyway são append-only. `V5__reporting_reader_day.sql` cria o
schema de leitura `reporting` sem alterar tabelas administrativas existentes:

- `reporting.reader_day`: agregados diários derivados da fronteira de eventos,
  com versão do cálculo, população, plataforma e watermark;
- `reporting.projection_health`: estado, frescor, contagem e versão da
  projeção.

O job de projeção é idempotente por `(day, population, platform)` e limitado a
366 dias. A população inicial é `UNCLASSIFIED`, portanto a API retorna
`UNTRUSTED` até que a classificação de provenance seja disponibilizada. Não há
payload bruto, subject ou texto de livro no snapshot.
