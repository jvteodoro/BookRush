# API

Adquire RDF e EPUB do Gutenberg, persiste snapshots RAW e coordena jobs, normalização TXT e chapters.json.

OIDC Keycloak com audiência bookrush-ingestion e papéis administrativos; não aceitar credenciais no request de ingestão.

O contrato machine-readable está em api/openapi.yaml, exportado de Springdoc. API Catalog o exibe; endpoints não são duplicados nesta página. Atualize o arquivo a partir da mesma revisão do serviço usando backstage/scripts/export-openapi.py. Breaking changes requerem revisão de consumidores; erros HTTP diferenciam autenticação, validação e indisponibilidade.
