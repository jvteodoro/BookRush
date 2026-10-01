# catalog-service

Mantém obras, edições, autores, identificadores e assets; autoriza distribuição por URL S3 temporária.

Leia [arquitetura](architecture/overview.md), [desenvolvimento](development/local-development.md), [API](api.md) e [runbook](operations/runbook.md).

Documentação transversal detalhada: [handbook](https://github.com/jvteodoro/BookRush/tree/main/docs).

A busca pública aceita título, título original, descrição, autor e subject. A migration V15 cria índices de lookup; os resultados continuam sob ownership do catálogo.

Reader clients use `/api/v1/books/{bookId}/reader-assets` to list active assets and request a non-cacheable, temporary download URL for approved content.
