# API

Adquire RDF, EPUB e a thumbnail de capa do Gutenberg, persiste snapshots RAW e coordena jobs, normalização TXT e chapters.json.

Quando `processAssets=true`, o job baixa `pg{id}.cover.medium.jpg`, grava o
snapshot RAW em `gutenberg/{id}/thumbnail.jpg` e registra no catalog-service um
asset `THUMBNAIL` com papel `COVER`. Para direitos explicitamente identificados
como public domain, o catálogo cria a decisão de distribuição correspondente e
aprova a thumbnail; demais casos permanecem sujeitos à revisão de direitos.

OIDC Keycloak com audiência bookrush-ingestion e papéis administrativos; não aceitar credenciais no request de ingestão.

O contrato machine-readable está em api/openapi.yaml, exportado de Springdoc. API Catalog o exibe; endpoints não são duplicados nesta página. Atualize o arquivo a partir da mesma revisão do serviço usando backstage/scripts/export-openapi.py. Breaking changes requerem revisão de consumidores; erros HTTP diferenciam autenticação, validação e indisponibilidade.
