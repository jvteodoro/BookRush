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

Os eventos de comportamento com `payload.platform` validado (`web` ou
`mobile`) alimentam linhas separadas de `reporting.reader_day`; valores
ausentes ou não permitidos permanecem `UNKNOWN`. A dimensão é coarse e não
contém user-agent, identidade ou identificador de dispositivo.

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

`V15__reporting_moderation_publisher_rights.sql` cria snapshots agregados para
aging de moderação, funil editorial/publicação e decisões de direitos/licenças.

`V16__reporting_recommendation_quality_daily.sql` cria outcomes atribuídos do
ledger de recomendações, preservando a versão de cálculo e o watermark.

`V17__reporting_book_engagement_daily.sql` cria fatos agregados por obra e por
idioma, com leitores distintos, minutos, aberturas, conclusões e interações.

`V18__reporting_event_schema_daily.sql` cria agregados de schema/cliente e
rejeições de telemetria, preservando apenas dimensões de baixa cardinalidade.

`V19__reporting_recommendation_position_daily.sql` agrega outcomes atribuídos
por bucket de rank para análise de relevância posicional.

`V20__reporting_editorial_quality_daily.sql` cria o snapshot diário de
qualidade editorial a partir das tabelas de campanhas de anotação. A projeção
é limitada a 366 dias, idempotente e preserva watermark/versão; discordâncias
e tempos são contagens/percentis agregados, sem texto ou identidade de
annotator.

`V21__reporting_excerpt_performance.sql` cria o snapshot diário protegido de
desempenho por excerpt. A projeção reconcilia o ledger de impressões com
outcomes `ATTRIBUTED`, preserva elegibilidade/modelo/estratégia e aplica
supressão de amostras pequenas na API; texto do excerpt e sujeitos nunca são
materializados no output.

`V22__reporting_experiment_daily.sql` cria guardrails agregados por assignment
persistido, variante e dia. A origem é o ledger de recommendation e a
atribuição `ATTRIBUTED`; sem assignment ativo a API retorna `NO_DATA`, e os
resultados continuam explicitamente observacionais.
