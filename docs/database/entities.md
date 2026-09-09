# Entidades implementadas

Dicionário das 14 tabelas e mapeamentos JPA. UUIDs são internos, gerados em Java;
SQL de ingestão deve fornecer IDs. Colunas opcionais toleram metadata incompleta.
Datas de criação/alteração usam TIMESTAMPTZ/Instant.


## asset_processing — AssetProcessing

Transformação entre versões; TEXT_EXTRACTION.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| input_asset_version_id | UUID | sim |
| output_asset_version_id | UUID | não |
| processing_type | String | sim |
| processor | String | sim |
| processor_version | String | sim |
| status | AssetProcessingStatus | sim |
| started_at | Instant | não |
| finished_at | Instant | não |
| metadata | Map<String, Object> | sim |
| error_message | String | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Relações: BookAssetVersion via input_asset_version_id, BookAssetVersion via output_asset_version_id.


## author — Author

Agente intelectual; Jane Austen.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| name | String | sim |
| normalized_name | String | não |
| birth_year | Short | não |
| death_year | Short | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Sem FK de saída; relações inversas constam no ER.


## book — Book

Obra intelectual; Pride and Prejudice.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| canonical_title | String | sim |
| original_title | String | não |
| original_language | String | não |
| first_publication_year | Short | não |
| description | String | não |
| status | BookStatus | sim |
| merged_into_id | UUID | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Relações: Book via merged_into_id.


## book_asset — BookAsset

Arquivo lógico e finalidade; EPUB SOURCE.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| book_id | UUID | sim |
| edition_id | UUID | não |
| asset_type | BookAssetAssetType | sim |
| asset_role | BookAssetAssetRole | sim |
| source_id | UUID | sim |
| license_id | UUID | não |
| status | BookAssetStatus | sim |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Relações: Book via book_id, Edition via edition_id, Source via source_id, License via license_id.


## book_asset_version — BookAssetVersion

Versão física; S3/books-source/key e SHA-256.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| book_asset_id | UUID | sim |
| version_number | Integer | sim |
| storage_provider | StorageProvider | sim |
| bucket | String | sim |
| object_key | String | sim |
| original_filename | String | não |
| content_type | String | não |
| size_bytes | Long | não |
| sha256 | String | não |
| status | BookAssetVersionStatus | sim |
| created_at | Instant | sim |
| updated_at | Instant | sim |
| ingestion_item_id | UUID | não |

Relações: BookAsset via book_asset_id, IngestionItem via ingestion_item_id.


## book_author — BookAuthor

Crédito ordenado de uma obra; AUTHOR posição 1.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| book_id | UUID | sim |
| author_id | UUID | sim |
| role | BookAuthorRole | sim |
| position | Integer | sim |

Relações: Book via book_id, Author via author_id.


## edition — Edition

Manifestação de uma obra; edição inglesa.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| book_id | UUID | sim |
| title | String | não |
| subtitle | String | não |
| language | String | não |
| publisher | String | não |
| publication_year | Short | não |
| edition_name | String | não |
| description | String | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |
| license_id | UUID | não |

Relações: Book via book_id, License via license_id.


## edition_author — EditionAuthor

Crédito específico da edição; TRANSLATOR posição 1.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| edition_id | UUID | sim |
| author_id | UUID | sim |
| role | EditionAuthorRole | sim |
| position | Integer | sim |

Relações: Edition via edition_id, Author via author_id.


## external_identifier — ExternalIdentifier

Identidade externa com exatamente um alvo; Gutenberg 1342 aponta à edição.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| source_id | UUID | sim |
| book_id | UUID | não |
| edition_id | UUID | não |
| author_id | UUID | não |
| identifier_type | ExternalIdentifierIdentifierType | sim |
| identifier_value | String | sim |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Relações: Source via source_id, Book via book_id, Edition via edition_id, Author via author_id.


## ingestion_item — IngestionItem

Tentativa individual; Gutenberg 11 FAILED sem obra.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| ingestion_job_id | UUID | sim |
| source_id | UUID | sim |
| external_identifier | String | sim |
| attempt_number | Integer | sim |
| source_record_id | UUID | não |
| book_id | UUID | não |
| edition_id | UUID | não |
| status | IngestionItemStatus | sim |
| started_at | Instant | não |
| finished_at | Instant | não |
| error_code | String | não |
| error_message | String | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Relações: IngestionJob via ingestion_job_id, Source via source_id, SourceRecord via source_record_id, Book via book_id, Edition via edition_id.


## ingestion_job — IngestionJob

Execução; COMPLETED_WITH_ERRORS.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| source_id | UUID | sim |
| started_at | Instant | não |
| finished_at | Instant | não |
| status | IngestionJobStatus | sim |
| trigger_type | IngestionJobTriggerType | sim |
| items_discovered | Integer | sim |
| items_processed | Integer | sim |
| items_succeeded | Integer | sim |
| items_failed | Integer | sim |
| error_message | String | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Relações: Source via source_id.


## license — License

Metadata de licença; UNKNOWN não concede permissões.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| code | String | sim |
| name | String | sim |
| url | String | não |
| commercial_use_allowed | Boolean | não |
| modification_allowed | Boolean | não |
| redistribution_allowed | Boolean | não |
| attribution_required | Boolean | não |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Sem FK de saída; relações inversas constam no ER.


## source — Source

Origem genérica; GUTENBERG.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| code | String | sim |
| name | String | sim |
| base_url | String | não |
| source_type | SourceSourceType | sim |
| is_active | Boolean | sim |
| created_at | Instant | sim |
| updated_at | Instant | sim |

Sem FK de saída; relações inversas constam no ER.


## source_record — SourceRecord

Revisão de metadata original JSONB; resposta Gutenberg.

| Campo | Tipo Java | Obrigatório |
|---|---|---|
| id | UUID | sim |
| source_id | UUID | sim |
| external_id | String | sim |
| raw_metadata | Map<String, Object> | sim |
| retrieved_at | Instant | sim |
| content_hash | String | sim |
| created_at | Instant | sim |

Relações: Source via source_id.


## Contratos compartilhados

Enums são persistidos por nome, nunca ordinal. PostgreSQL usa VARCHAR + CHECK.
Papéis de crédito possuem AUTHOR/EDITOR/TRANSLATOR/ILLUSTRATOR/CONTRIBUTOR.
Datas bibliográficas desconhecidas são NULL; anos de -9999 a 9999 exceto zero.
Idiomas usam ISO 639-1 quando existente, senão ISO 639-3, lowercase; a DDL valida
sintaxe de 2–3 letras, não consulta o catálogo ISO. Nomes e títulos não são únicos.

Identificadores exigem exatamente uma FK book/edition/author. ISBN10/13,
Gutenberg, Open Library edition, Internet Archive, LCCN e OCLC apontam à edição;
Open Library work à obra; DOI à obra/edição; Wikidata pode apontar aos três.
Unicidade é fonte/tipo/valor. ISBN é armazenado sem separadores (X uppercase
no ISBN10); DOI lowercase; Gutenberg/OCLC decimal positivo sem zeros à esquerda;
Open Library OL+número+W/M e Wikidata Q+número. A DDL verifica formatos,
não checksum ISBN, identidade remota ou confiabilidade. Conflitos de metadata
exigem revisão na ingestão, sem substituir silenciosamente o alvo.

Licenças têm permissões nullable: NULL é desconhecido. Os seis rótulos iniciais
não concedem automaticamente direitos. Asset pode especificar licença distinta
da edição; ver storage-model.md.

JPA mapeia FKs como UUID que controla escrita e associações LAZY para leitura.
Após mudar a FK de um objeto já carregado, refresh/clear antes de navegar pela
associação evita cache obsoleto. Não serializar entidades como respostas REST.
Não há cascades ou coleções inversas ilimitadas. Identidade de objeto é mantida
em equals/hashCode; apenas chaves compostas de créditos comparam valores.
Triggers mantêm updated_at inclusive em SQL; refresh obtém o timestamp final
do banco após UPDATE via JPA.

Repositories existem somente para obra por ID externo, assets paginados e
versões por asset/hash/última disponível. Outras entidades usam EntityManager
até surgir uma operação concreta que justifique repository.

Regras detalhadas: [schema](schema.md), [storage](storage-model.md),
[ingestão](ingestion-model.md), [linhagem](lineage.md).

Dados de referência mínimos: fontes GUTENBERG, OPEN_LIBRARY, INTERNET_ARCHIVE,
ADMIN_UPLOAD, INTERNAL_PIPELINE; source_type DIGITAL_LIBRARY, EXTERNAL_CATALOG,
ADMIN_UPLOAD, INTERNAL_PIPELINE. Licenças PUBLIC_DOMAIN, CC0, CC_BY, CC_BY_SA,
CC_BY_NC, UNKNOWN, todas com permissões inicialmente NULL. Registro literário
Gutenberg 1342 existe somente na fixture de teste.
