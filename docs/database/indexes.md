# Índices existentes (V2–V3)

| Índice/constraint | Consulta ou integridade atendida |
| --- | --- |
| PK de cada entidade | Identidade interna UUID e referências FK |
| book_canonical_title_idx | Igualdade/ordenação de título; não unique nem fuzzy |
| edition_book_idx | Edições de uma obra |
| PK book_author / edition_author | Créditos por obra/edição e unicidade de autor/papel |
| book_author_credit_order / edition_author_credit_order | Ordem única de créditos; adiável na reordenação |
| book_author_author_idx / edition_author_author_idx | Obras/edições creditadas ao autor |
| edition_id_book | Alvo para futura FK composta, evita edição de obra errada |
| book_merge_target_idx | Lookup inverso de merge, somente não nulos |
| source.code / license.code UNIQUE | Vocabulário e lookup por código |
| edition_license_idx | Edições que apontam uma licença, somente não nulos |
| external_identifier_scope UNIQUE | Fonte/tipo/valor identifica uma alegação única |
| external_identifier_lookup_idx | Tipo/valor entre fontes, não coberto pelo prefixo source da UNIQUE |
| external_identifier_book_idx | Identificadores de obra, somente não nulos |
| external_identifier_edition_idx | Identificadores de edição, somente não nulos |
| external_identifier_author_idx | Identificadores de autor, somente não nulos |

Não criamos índices redundantes nos prefixos de PK/UNIQUE. Nenhum índice único
para título/nome/ISBN entre fontes. Índices de estado booleano, fuzzy/trigram,
full-text e JSONB aguardam workload real; não se infere ganho de performance
com poucos registros de teste. Índices de assets/ingestão são entregas futuras.

## V4

- book_asset: índices por obra, edição/obra, fonte e licença (os opcionais
  são parciais), para listagem e verificação de referências.
- UNIQUE(asset, version_number) também atende listagem de todas as versões.
- UNIQUE(provider, bucket, object_key) atende identidade física. Com provider
  S3 fixo, não duplicamos um índice apenas por bucket/key.
- SHA-256 parcial, não unique, permite localizar conteúdo repetido.
- Índice parcial (asset, version_number DESC) WHERE status='AVAILABLE' atende
  a consulta da última versão disponível.
- Índices isolados de status foram adiados: baixa seletividade e ausência de
  uma carga operacional medida que justifique seu custo de escrita.

## V5–V6

ingestion_job(source_id,created_at DESC,id DESC) atende último job por fonte,
com desempate determinístico. ingestion_item(job,status) localiza falhas; o
unique(job,external_identifier,attempt_number) mantém retries distintos.
Índices parciais de record/book/edition e versão/ingestion_item atendem
proveniência e checagens de referência. Unique de source_record por
(source,external_id,content_hash) permite revisões sem duplicar payload idêntico.

asset_processing possui índices input e output (este parcial); a consulta
recursiva busca arestas por input. Não há GIN de JSONB ou índices isolados de
status: não existem consultas de produto que os justifiquem. EXPLAIN das oito
consultas documentadas é executado no teste; uma fixture pequena não demonstra
ganho de performance real. Medir uma carga representativa antes de acrescentar
índices ou afirmar latência.
