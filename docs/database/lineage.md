# Linhagem implementada em V6

asset_processing registra uma tentativa de transformação entre versões físicas,
com processador, sua versão, tipo da operação, estado, horários, metadata JSONB
variável e erro. IDs e estado não ficam escondidos em JSON. Input é obrigatório;
output pode faltar em tentativa pendente/falha, mas é exigido em SUCCEEDED.
Tipo é um código extensível uppercase (ex.: NORMALIZATION, TEXT_EXTRACTION,
CHAPTER_SEGMENTATION), e não um enum fechado de todos os pipelines futuros.

O modelo mínimo é uma entrada/uma saída por registro. Para múltiplas saídas,
registrar uma aresta por saída; não é um orquestrador de execução multi-input.
Retries recebem novos IDs e preservam as tentativas anteriores. Multi-input
exigirá uma entidade de execução com tabelas de entradas/saídas quando houver
produtor real. Todas as FKs são RESTRICT.

Autoaresta é rejeitada no banco. Ciclos indiretos não são bloqueados na DDL;
um futuro coordenador deve validá-los sob concorrência. As consultas recursivas
usam caminho visitado, reportam is_cycle e encerram aquele ramo para não travar
mesmo com dados incorretos. Essa detecção é testada com ciclo de duas versões.
Somente arestas SUCCEEDED participam da linhagem derivada.

```mermaid
flowchart TD
    Source[Gutenberg] --> Record[source_record]
    Record --> Item[ingestion_item]
    Item --> V1[source EPUB v1]
    V1 -->|NORMALIZATION| V2[normalized EPUB v1]
    V2 -->|TEXT_EXTRACTION| V3[TXT v1]
    V3 -->|CHAPTER_SEGMENTATION| V4[chapters JSON v1]
```

A consulta recursiva executável está em [queries.sql](queries.sql). O caminho
reconstrói versões e processadores; a versão inicial chega à origem via
ingestion_item/source_record ou asset/source. O fixture não contém objetos S3
reais e os processadores ainda não foram implementados.
