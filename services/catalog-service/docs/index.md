# catalog-service

Mantém obras, edições, autores, identificadores e assets; autoriza distribuição por URL S3 temporária.

Leia [arquitetura](architecture/overview.md), [desenvolvimento](development/local-development.md), [API](api.md) e [runbook](operations/runbook.md).

Documentação transversal detalhada: [handbook](https://github.com/jvteodoro/BookRush/tree/main/docs).

A busca pública aceita título, título original, descrição, autor e subject. A
API executa a busca no PostgreSQL com paginação, ranking textual e fallback de
correspondência parcial; o frontend não baixa o catálogo inteiro para filtrar
no navegador. A migration V16 adiciona os índices GIN/trigram necessários.

Reader clients use `/api/v1/books/{bookId}/reader-assets` to list active assets and request a non-cacheable, temporary download URL for approved content.

## Categorias bibliográficas

GET `/api/v1/books` e `/api/v1/books/{bookId}` (e aliases internos) retornam
`categories: [{id, name, scheme}]`, obtidas das associações existentes
`catalog.book_subject` → `catalog.subject`. Somente subjects ACTIVE são
exibidos, sem duplicar uma categoria atribuída por várias fontes. A classificação
é carregada em uma consulta por página, evitando N+1; a busca já aceita subjects.
Sem classificação, o contrato retorna `[]`; idioma continua em `originalLanguage`
e nunca é usado como categoria. A ingestão Gutenberg já registra subjects com
proveniência; esta alteração não inventa categorias nem reclassifica dados.
A rota pública de metadados continua pública, sem bearer do realm produto.
