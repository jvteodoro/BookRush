# Componentes

Jobs e aquisição; utiliza também tabelas operacionais no schema catalog.

IngestionJobService persiste submissões; IngestionDispatcher coordena execução e leases; GutenbergImportService liga aquisição, API canônica e derivados. CanonicalCatalogPort mantém a fronteira HTTP.
