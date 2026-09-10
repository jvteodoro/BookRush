# Aquisição Gutenberg

O dispatcher transforma cada item selecionado em uma tarefa durável. Para um ID Gutenberg `N`, o serviço consulta somente os endpoints derivados do catálogo oficial (`https://www.gutenberg.org/cache/epub/N/pgN.rdf`), seguindo redirects apenas quando o destino continua no allowlist Gutenberg. O request administrativo recebe IDs e filtros; não recebe uma URL.

Cada RDF é baixado por streaming para um arquivo `.part`, limitado por `ingestion.max-record-bytes`, renomeado atomicamente após o hash SHA-256 e publicado no bucket privado `books-raw` com chave `gutenberg/{id}/{id}.rdf`. O registro `source_record` preserva o metadata, hash e locator. O parser StAX desabilita DTD e entidades externas.

Após o snapshot, o registro é aplicado ao `catalog-service` pelo comando canônico idempotente. O item recebe a referência ao `source_record`, `book_id` e `edition_id`; falhas permanecem no job e na task para retry/reconciliação. O processamento de EPUB/TXT e a criação de assets SOURCE/PUBLIC são etapas posteriores do pipeline.
