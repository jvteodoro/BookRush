# Identidade das marcações Readium — V10

V5 já criou `bookmarks.locator` como JSONB, limitado a objetos ou null. V10
preserva todos os registros e substitui o índice único de codepoint por dois
índices parciais: legado por `(subject_key, book_id, position_codepoint)` quando
locator é null; Readium por `(subject_key, book_id, md5(locator::text))` quando
locator existe. A representação JSONB normaliza a ordem das propriedades e o
hash evita ultrapassar o limite de tamanho de entradas B-tree com locators que
contêm contexto textual. MD5 é somente uma chave de deduplicação, sem função de
segurança. Existe risco teórico de colisão de hash entre locators diferentes.

O upsert usa o predicado do índice correspondente e `RETURNING`, preservando o
ID e created_at quando se repete uma posição. Para locators, codepoint fica em
zero: não deve ser interpretado como percentual nem usado para retomada. O href
versionado e a posição de recurso são a referência de navegação. Não se tenta
converter o percentual incorreto gravado pelos clientes anteriores.

Execute `BookmarkDatabaseTest` contra um PostgreSQL descartável vazio indicado
em `BOOKMARK_TEST_JDBC_URL`, com as credenciais de fixture documentadas no índice
do serviço. A cobertura migra de V9 para V10 com um registro legado, revalida,
repete a migration e verifica deduplicação, posições próximas, isolamento entre
contas e tipos JDBC. Use outro banco descartável vazio para a aplicação integral
das migrations. Não use volumes ou credenciais de produção nesses testes.
