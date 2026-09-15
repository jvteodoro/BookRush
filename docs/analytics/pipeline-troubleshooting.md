# Lições do pipeline Gutenberg → Analytics

Esta página registra os problemas encontrados durante a validação com os livros Gutenberg **84** e **1342**. Ela é um guia operacional para futuras execuções e deve ser atualizada quando o contrato de autenticação, storage ou assets mudar.

## Fluxo validado

```text
Keycloak (usuário operador)
  → book-ingestion-service
  → Gutenberg RDF/EPUB
  → SeaweedFS/S3 (RAW)
  → catalog-service (obra, edição, subjects e assets)
  → texto normalizado TXT
  → book-analytics-service
```

A execução bem-sucedida usou um job de ingestão persistente e um job de analytics referenciando diretamente as versões TXT. O analytics nunca deve receber somente o `book_id`: a entrada é uma `book_asset_version` textual exata.

## Incidentes e correções

### 1. `401 Unauthorized` ao processar cada livro

**Sintoma.** O job era criado, mas os dois itens terminavam como `FAILED` com `Unauthorized: 401 : [no body]`.

**Causa.** O `SubjectCatalogClient` ainda enviava `CANONICAL_SERVICE_TOKEN` legado, embora a execução estivesse configurada para client credentials OIDC. Além disso, os clients técnicos recebiam um parâmetro `scope` que não estava declarado como client scope no realm administrativo. O token sem esse parâmetro já recebia os scopes padrão configurados no Keycloak.

**Correção.**

- `SubjectCatalogClient` passou a obter o token canônico pelo `OidcServiceTokenProvider`.
- O provider só envia `scope` quando ele está explicitamente configurado.
- Os clients técnicos usam seus scopes padrão no Keycloak quando não há scope solicitado.
- Credenciais de catálogo e assets permanecem separadas.

**Prevenção.** Testar cada client com `grant_type=client_credentials` antes de iniciar um job e conferir `iss`, `aud` e `scope` do token sem registrar o token. Não reativar automaticamente o bearer legado.

### 2. `403 Forbidden` na atribuição de subjects

**Sintoma.** A autenticação do catálogo aparecia válida, mas o item falhava durante a importação de subjects.

**Causa.** O valor enviado como `assignment_method` era `SOURCE_METADATA`. A migration V14 restringe a coluna aos valores `SOURCE`, `MATCHED`, `CURATED` e `MODEL`. O erro de integridade acabava sendo observado pelo cliente como resposta HTTP sem detalhe útil.

**Correção.** Gutenberg envia `SOURCE`; a proveniência detalhada continua em `sourceCode` e `sourceRecordId`.

**Prevenção.** Validar enums do contrato contra as constraints da migration antes do deploy. Quando uma chamada interna falhar, preservar no erro o endpoint e o status, sem incluir Authorization ou segredos.

### 3. `400 Invalid asset request` ao repetir um EPUB SOURCE

**Sintoma.** A segunda execução tentava chamar `/{assetId}/versions` e recebia `400 Invalid asset request`.

**Causa.** Assets com papel `SOURCE` são imutáveis no catálogo. A operação de criar versão é reservada aos tipos/fluxos permitidos; não é um mecanismo de sobrescrita para EPUB SOURCE.

**Correção.** Em replay, o cliente verifica se o asset pertence à mesma edição e reutiliza sua versão física mais recente. Assets de outra edição não são reutilizados. Nenhuma versão SOURCE antiga é alterada.

**Prevenção.** Diferenciar explicitamente no cliente:

- replay idempotente de um asset SOURCE existente;
- criação de um novo asset para outra edição;
- versionamento permitido de assets de processamento.

A edição precisa fazer parte da chave de compatibilidade; apenas comparar `book_id`, tipo e papel é insuficiente.

### 4. Contadores incorretos após retries

**Sintoma.** `items_processed` podia superar a quantidade descoberta quando uma tarefa era repetida.

**Causa.** Tentativas eram contadas como itens lógicos.

**Correção.** Os contadores agora são derivados dos estados de `ingestion_item` (`SUCCEEDED`, `FAILED`, `CANCELLED`). Retries permanecem em `attempt_count` da tarefa.

**Prevenção.** Validar sempre a invariável:

```text
items_processed = items_succeeded + items_failed + items_cancelled
```

Retentativas não podem alterar `items_discovered`.

### 5. Versão TXT não aparecia no item de ingestão

**Sintoma.** O item terminava com `book_id` e `edition_id`, mas `asset_version_id` aparecia nulo na API do job.

**Causa.** A relação histórica da versão textual é mantida no catálogo; o item de ingestão não era atualizado automaticamente com esse identificador em todos os caminhos de replay.

**Procedimento seguro.** Para iniciar analytics, consultar as versões `TXT`/`PROCESSING` no catálogo e usar o ID retornado. O job de analytics registra essa FK como sua entrada. Não inventar um UUID e não usar a versão de outro livro.

**Melhoria futura.** Se a API de ingestão precisar expor esse vínculo como parte do contrato, fazê-lo por alteração aditiva e teste de replay; não alterar migrations aplicadas.

## Procedimento reproduzível

1. Garanta que o `.env` contenha os endpoints, clients e credenciais autorizadas. Nunca copie seus valores para Git, logs ou beads.
2. Obtenha um token de operador no realm `bookrush` para chamar a API administrativa de ingestão.
3. Execute um job pequeno com `externalIds` explícitos, `processAssets=true` e um `Idempotency-Key` novo.
4. Aguarde `COMPLETED` e confirme `items_succeeded == items_discovered`.
5. Liste no catálogo as versões `TXT` com papel `PROCESSING` correspondentes aos livros.
6. Crie o job de analytics com esses IDs de `book_asset_version` e uma `operationKey` estável.
7. Aguarde `COMPLETED` e verifique que cada item possui `analysis_run_id`.
8. Execute a suíte offline antes de ampliar o lote:

```bash
bash scripts/test-analytics-e2e.sh
```

Na validação de 15/09/2026, a ingestão dos dois livros concluiu 2/2 itens e o analytics concluiu 2/2 versões. A suíte offline passou com 34 testes e zero falhas.

## Diagnóstico sem exposição de segredos

É permitido registrar:

- ID do job e da tarefa;
- etapa lógica;
- status HTTP;
- issuer e audience validados;
- código de erro sanitizado;
- IDs de versões e duração.

Nunca registrar:

- access token, refresh token ou client secret;
- senha do usuário operador;
- cabeçalho `Authorization` completo;
- cookies, URLs assinadas ou conteúdo integral do livro.

Para distinguir falha de autenticação de falha de autorização, primeiro valide o token no endpoint OIDC e depois faça uma chamada mínima à rota interna. Um `401` indica token ausente/inválido; um `403` indica identidade válida sem a autoridade exigida ou uma regra de domínio. Erros de constraint devem ser corrigidos no contrato, não contornados relaxando a segurança.

## Limitações conhecidas

A execução validou o pipeline barato e offline do analytics. Modelos spaCy, BGE-M3, NLI e classificador supervisionado continuam dependentes de artefatos versionados e preparados explicitamente; eles não devem ser baixados no startup de produção. O Maven direto no host pode falhar quando `~/.m2` estiver somente leitura; a compilação Docker continua sendo a validação reproduzível nesse ambiente.
