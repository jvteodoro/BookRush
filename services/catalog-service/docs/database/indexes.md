# Índices

Identificadores usam fonte/tipo/valor; versões usam asset/versão e identidade S3; hash tem índice não único. Filas consultam estado, leases e próximas tentativas. A busca usa o índice GIN `book_search_tsvector_idx` para texto e índices GIN trigram para correspondências parciais em títulos, autores e assuntos, criados pela V16. O inventário definitivo está em docs/database/indexes.md no handbook. Analise EXPLAIN antes de adicionar índice.
