# Nomes das marcações — V11

A migration adiciona `name VARCHAR(120) NOT NULL DEFAULT ''` a
`reader_state.bookmarks`, preservando IDs, locators, notas e datas. As marcações
existentes recebem nome vazio e a interface mantém o fallback do capítulo.
Não há índice único por nome: o usuário pode dar o mesmo nome a posições distintas.

POST novo aceita nome e os clientes legados continuam funcionando sem ele.
O upsert preserva o nome existente quando o cliente não envia name. PUT por ID
altera somente name; DELETE remove somente a marcação do principal e livro
informados. O nome é normalizado e validado na API antes de escrever no banco.

`BookmarkDatabaseTest`, habilitado por `BOOKMARK_TEST_JDBC_URL`, deve usar um
PostgreSQL descartável vazio com usuário `postgres` e senha de fixture
`bookrush-test`. A cobertura aplica V9, cria um registro legado, aplica V10,
cria um locator, aplica V11 e confirma a preservação. Depois testa rename sem
alterar posição/data, upsert legado sem apagar nome, isolamento de conta/livro,
exclusão e revalidação repetida das migrations. Nunca use volumes de produção.

No ambiente Windows da implementação, Docker está indisponível; os testes de
banco ficam condicionais. Build, testes unitários, proxy HTTP do BFF e navegador
com fixture local são evidências locais e não substituem PostgreSQL nem smoke
autenticado através da revisão efetivamente implantada.
