# Thumbnails do catálogo

Este documento registra o caminho completo que faz uma thumbnail Gutenberg chegar à biblioteca e serve de runbook para novos diagnósticos.

## Por que falhou originalmente

Houve quatro falhas independentes que se combinaram:

1. A thumbnail era importada e registrada no PostgreSQL, mas o livro permanecia `REVIEW_REQUIRED`. O método de link público exigia `ACTIVE` para qualquer livro e devolvia 404 antes de considerar o asset aprovado.
2. A rota passou por mudanças de autenticação. O realm do produto (`bookrush`) usa o emissor do produto, `aud=account` e o escopo `bookrush.catalog.read`; o realm de plataforma usa outro emissor e audiência (`bookrush-catalog-admin`). Reutilizar o decoder ou audience converter errado causava 401/403.
3. O frontend usa uma imagem autenticada. Um `<img src>` não envia o bearer por conta própria, portanto `AuthenticatedImage` precisa buscar o binário/blob com o token e criar uma URL local. Remover a autenticação da rota seria um bypass inseguro.
4. O reprocessamento baixa/cria o asset, mas não é aprovação. Importação, aprovação do asset, direitos e ativação do livro são estados distintos; reimportar um livro já existente não transforma automaticamente `REVIEW_REQUIRED` em `ACTIVE`.

## Correções aplicadas

- O decoder da cadeia de produto foi separado do decoder da cadeia de catálogo/plataforma. A validação considera emissor, JWK e audiência corretos, e o conversor mapeia explicitamente `bookrush.catalog.read` para a autoridade usada pela API.
- A cadeia de thumbnail mantém bearer obrigatório. Sem token o resultado esperado é 401; com token válido, a API só entrega uma thumbnail cujo asset e versão estão ativos, o objeto existe no storage e os direitos permitem distribuição.
- A regra de entrega foi refinada: uma capa aprovada pode ser entregue para um livro `REVIEW_REQUIRED`, porque a thumbnail é um asset de apresentação independente. Conteúdo textual e demais links continuam exigindo livro `ACTIVE`.
- O tratamento de `DispatcherType.ERROR` foi permitido para que uma ausência real permaneça 404, sem virar uma segunda tentativa de autenticação que mascara o diagnóstico.
- Logs de autenticação e storage foram mantidos sem bearer, cookies ou segredos.
- O fluxo de ingestão continua usando `processAssets=true`; escopos S2S vazios no Compose significam usar os default client scopes do Keycloak. Escopos fixos só devem ser configurados quando estiverem realmente presentes no token.
- O cliente `labsoft-admin-web` agora recebe `bookrush.catalog.read`, `bookrush.catalog.write`, `bookrush.assets.write` e a audiência `bookrush-catalog-admin`. Após reconciliar o realm, o admin pode consultar e aprovar a fila.
- O admin ganhou a rota **Review Queue**. Ela lista livros `REVIEW_REQUIRED`, mostra estado do asset/versão e permite aprovar livro e thumbnail separadamente. A API correspondente é `GET /api/admin/books/review`, `POST /api/admin/books/{bookId}/approve` e o endpoint existente `POST /api/admin/books/{bookId}/assets/{assetId}/approve`.

## Checklist de diagnóstico

1. Confirme o container/imagem efetivamente implantado e consulte `/actuator/health`.
2. Sem token, `GET /api/v1/books/{id}/thumbnail` deve responder 401.
3. Com token do frontend, confirme no payload apenas (sem registrar o token): `iss` do realm `bookrush`, `aud=account` e escopo `bookrush.catalog.read`.
4. Consulte a fila administrativa e verifique: livro, asset `THUMBNAIL/COVER`, asset `ACTIVE`, versão `AVAILABLE`, `availability_status=AVAILABLE`, direitos de distribuição e bucket/object key existentes.
5. Se não houver asset elegível, reprocesse a importação com `processAssets=true`; se o asset existir, aprove-o na Review Queue; depois aprove o livro quando a revisão editorial terminar.
6. Se o banco indicar disponível e o objeto não existir, corrija o storage/recovery antes de reimportar. Não publique bucket, desative bearer ou copie tokens para logs.

## Novos cenários

Para outra fonte de livros, reproduza a mesma sequência: persistir metadata e proveniência, criar versão física, validar hash e storage, registrar direitos, aprovar o asset, e só então aprovar o livro. A ausência de uma dessas transições deve aparecer na fila, não ser compensada por bypass de autenticação.
