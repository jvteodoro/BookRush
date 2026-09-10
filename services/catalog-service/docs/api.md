# API

Mantém obras, edições, autores, identificadores e assets; autoriza distribuição por URL S3 temporária.

Tokens administrativos de assets e token interno canônico; leitura pública somente nas rotas explicitamente permitidas.

O contrato machine-readable está em api/openapi.yaml, exportado de Springdoc. API Catalog o exibe; endpoints não são duplicados nesta página. Atualize o arquivo a partir da mesma revisão do serviço usando backstage/scripts/export-openapi.py. Breaking changes requerem revisão de consumidores; erros HTTP diferenciam autenticação, validação e indisponibilidade.
