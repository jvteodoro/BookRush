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

`V6__reporting_feed_and_pipeline.sql` adiciona:

- `reporting.feed_daily`, com entrega do ledger, viewability e outcomes
  atribuídos pela versão `attribution-v1`; a chave inclui dia, estratégia,
  modelo, caminho, população e plataforma;
- `reporting.pipeline_snapshot`, com estados de itens e fases de analytics,
  idade do pending mais antigo, falhas, throughput na última hora e watermark.

A projeção utiliza somente agregações limitadas aos últimos 366 dias e atualiza
as linhas existentes por chave. Não há exclusão de fatos operacionais nem
consultas SQL fornecidas pelo frontend.
