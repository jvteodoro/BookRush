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

`V7__reporting_data_quality.sql` cria as projeções diárias de qualidade. Elas
são reconstruídas apenas na janela materializada, possuem watermark e versão de
cálculo, e alimentam os painéis DQ-03 (atribuição) e DQ-04 (reconciliação).

`V8__reporting_reader_activity_windows.sql` adiciona as colunas de atividade
semanal/mensal e recorrência ao snapshot diário sem reescrever migrations
anteriores.

`V9__reporting_social_publisher.sql` cria os agregados diários usados pelas
áreas Social & Publisher. Ausência de registros retorna `NO_DATA` e não é
convertida em atividade zero pelo frontend.

`V10__reporting_content_supply_and_ingestion.sql` adiciona:

- `reporting.content_supply_snapshot`, um snapshot temporal da oferta de livros
  ativos, assets disponíveis, assets analisados e excerpts elegíveis/rankeados;
- `reporting.ingestion_daily`, uma projeção diária de jobs e itens por estado,
  incluindo descobertos, processados, sucesso e falhas.

As consultas usam somente contagens agregadas dos bounded contexts catalog e
analytics, preservam `source_watermark` e `calculation_version`, e não expõem
IDs, conteúdo ou mensagens de erro brutas no contrato do dashboard.

`V11__reporting_pipeline_phase_daily.sql` agrega `analysis_phase_timing` por
dia, fase, estado e código de erro, calculando percentis diretamente sobre as
durações observadas e contando tentativas, falhas e leases presos.

`V12__reporting_reading_and_retention.sql` cria `reporting.reading_daily` para
sessões, duração, livros e biblioteca, e `reporting.retention_cohort` para
coortes D1/D7/D30. A coluna `mature` impede que uma coorte incompleta seja
interpretada como retenção zero.

`V13__reporting_recommendation_daily.sql` agrega o ledger de recommendation por
modelo, estratégia, caminho e plataforma, preservando contagens de requests,
itens, impressões, viewability, livros únicos e percentis de rank.

`V14__reporting_event_quality_daily.sql` agrega eventos aceitos/rejeitados e
estado do outbox por dia, expondo somente contagens, tentativas e idade do item
pendente.
