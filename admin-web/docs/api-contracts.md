# Contratos de integração

O portal é uma SPA administrativa. Ele não possui banco próprio nem replica
regras de domínio: cada alteração chama o serviço dono, com o bearer JWT do
realm `bookrush-platform`.

## Catálogo e ingestão

`GET /ingestion/api/admin/v1/ingestion/gutenberg/catalog?page=0&size=50&q=...`
consulta o índice paginado de livros Gutenberg (via Gutendex), retornando IDs
externos, título, autor, idioma e subjects. A paginação permite percorrer o
catálogo inteiro sem carregar todos os livros no browser; o campo `next` do
índice controla a habilitação de “Próxima página” e todas as linguagens
disponíveis são incluídas. A importação usa
`POST /ingestion/api/admin/v1/ingestion/run` com
`Idempotency-Key` e o payload `source`, `externalIds`, `languages`, `maxItems`,
`dryRun` e `processAssets`. O portal acompanha
`GET /ingestion/api/admin/v1/ingestion/jobs/{id}` até um estado terminal.
Para uma obra já importada, o portal envia `reprocess=true`: isso cria uma
execução nova, preserva o controle de idempotência da requisição e permite
atualizar EPUB, derivados e thumbnails sem duplicar o livro canônico.

O endpoint de catálogo consulta somente metadados do índice; não baixa EPUB,
PDF ou TXT. Os assets só são adquiridos depois que a operação de ingestão é
explicitamente disparada.

## Analytics

O portal cria jobs em `POST /analytics/api/admin/v1/content-analytics/jobs`,
sempre com uma chave idempotente e a versão de configuração solicitada. O
adapter primeiro consulta os assets administrativos do catálogo e seleciona a
versão `AVAILABLE` do TXT normalizado; nunca envia o UUID abstrato do livro
como se fosse uma versão textual. O estado é consultado em
`GET /analytics/api/admin/v1/content-analytics/jobs/{id}`.
Os adapters não expõem URLs permanentes de object storage.

## Feed Lab

As rotas `/feed/admin/training-sessions`, `/feed/admin/feed`,
`/feed/admin/events` e `/feed/admin/training-sessions/{id}/parameters` são
contratos administrativos do serviço de feed. O usuário é obtido do JWT; o
browser nunca envia um `userId` confiável. Eventos carregam `eventId` para
deduplicação e parâmetros são imutáveis por versão.

Quando um serviço ainda não estiver implantado, o portal pode operar em
`VITE_USE_MOCKS=true`; o modo de produção falha com uma mensagem sanitizada e
não converte erro de rede em sucesso.
