# Assets SOURCE

Quando `processAssets=true`, o worker seleciona a representação EPUB indicada
no RDF Gutenberg, baixa o arquivo pela mesma política de allowlist e limites,
grava uma cópia RAW em `books-raw` e envia o EPUB validado ao endpoint
administrativo do catálogo. O catálogo calcula o SHA-256, grava a versão em
`books-source` e confirma a leitura por HEAD antes de marcar a versão como
`AVAILABLE`.

O token usado para essa chamada é `INGESTION_ASSET_TOKEN`, configurado fora do
Git. Ele é independente do `CANONICAL_SERVICE_TOKEN`. Sem o token, a ingestão
continua somente com metadados e registra a operação como não executada.

O catálogo deduplica versões SOURCE pelo hash. Bytes iguais retornam a versão
existente; bytes diferentes criam uma nova versão, preservando a anterior.
Falhas após o PUT deixam a versão persistida como `FAILED` para reconciliação,
sem exclusão cega do objeto.
