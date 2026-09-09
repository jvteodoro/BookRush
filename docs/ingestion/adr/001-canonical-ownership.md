# ADR-001 — Ownership canônico e contrato entre serviços

## Contexto

O `catalog-service` já possui o schema `catalog` controlado por Flyway V1–V6.
Ele contém `book`, `author`, `edition`, associações de créditos,
`external_identifier`, `source`, `license`, `book_asset`,
`book_asset_version`, `source_record`, `ingestion_job`, `ingestion_item` e
`asset_processing`. O serviço de ingestão precisa importar dados e arquivos sem
duplicar essas entidades ou criar um segundo executor de migrations para a mesma
história.

Há duas dificuldades concretas na implementação existente. `ingestion_item`
modela a tentativa no próprio registro (`attempt_number`), enquanto a nova
pipeline precisa separar item lógico de tentativas e leases. Além disso,
`AssetService` hoje compensa um PUT apagando o objeto e não permite novas versões
de um asset com papel `SOURCE`. Essas regras precisam evoluir por migrations e
protocolo idempotente; não podem ser contornadas por tabelas `*_v2` no novo
serviço.

## Decisão

O catálogo canônico pertence exclusivamente ao `catalog-service`. O serviço de
ingestão acessa esse domínio através da porta `CanonicalCatalogPort`, cuja
implementação inicial é um cliente HTTP interno autenticado. O contrato será
versionado e terá comandos explícitos para upsert de obra, autor, edição,
identificadores, créditos, assets e finalização de versões.

Cada comando inclui:

```json
{
  "operationKey": "record/{source}/{type}/{externalId}/{revision}/canonicalize/v1",
  "requestHash": "sha256-do-corpo-canônico",
  "expectedRevision": 7,
  "fenceToken": 42,
  "payload": {}
}
```

O resultado distingue `CREATED`, `UPDATED`, `NOOP` e `CONFLICT` e retorna os
identificadores canônicos, a revisão efetivamente aplicada e um `commandId`.
Repetir a mesma chave com o mesmo hash devolve o mesmo resultado; repetir com
corpo diferente retorna HTTP 409. Timeout depois de o catálogo confirmar não é
tratado como falha nova: o cliente consulta o resultado por `operationKey` antes
de tentar outra escrita.

O catálogo verifica o `fenceToken` dentro da transação que altera o domínio. Um
worker cuja lease expirou pode continuar executando I/O, mas sua confirmação é
rejeitada no commit canônico. A validação apenas no estado local do worker não é
suficiente. O serviço de ingestão nunca mantém transação PostgreSQL aberta
durante uma chamada HTTP e não promete uma transação distribuída entre os dois
serviços.

O `catalog-service` será o único executor das migrations das tabelas canônicas e
das tabelas operacionais V1–V6 que ele já possui. A evolução para itens lógicos,
tentativas, leases, `storage_intent`, current elegível e linhagem multi-output
será feita por migrations forward compatíveis, com backfill explícito e sem
apagar o histórico existente. O novo serviço pode ter tabelas próprias para
snapshots, staging e execução somente depois de o escopo de cada tabela estar
registrado no mapa de ownership.

## Limites e compatibilidade

- PostgreSQL continua persistido no named volume Docker já adotado pelo projeto.
- Arquivos ficam no object storage; PostgreSQL guarda metadados, hashes,
  bucket/object key e provenance.
- `books-raw` é um bucket privado separado e não recebe `book_id` fictício.
- Keys antigas produzidas por `ObjectKeyBuilder` continuam válidas; novas keys
  determinísticas podem usar namespace de operação/hash sem reescrever objetos.
- A licença operacional existente não é tratada como autorização jurídica
  global. Direitos por ação, território, evidência e validade serão persistidos
  antes de habilitar distribuição.
- Se eventos forem necessários, primeiro será decidida uma outbox durável com
  envelope e `eventId`; nenhum broker é pressuposto por este ADR.

## Alternativas consideradas

### JDBC compartilhado pelo serviço de ingestão

Rejeitada como implementação inicial: permitiria uma transação local simples,
mas criaria dois donos para o mesmo modelo e acoplaria migrations e entidades
canônicas. Só pode ser reconsiderada por uma nova decisão que delimite
ownership, locking e histórico.

### Tabelas duplicadas no serviço de ingestão

Rejeitada. Duplicaria IDs, FKs, créditos e versões e tornaria reconciliação uma
suposição de negócio.

### Transação distribuída/XA

Rejeitada por complexidade operacional e incompatibilidade com object storage.
Usaremos idempotência, fencing, estados intermediários, retries e reconciliação.

## Consequências

O contrato HTTP exige tratamento explícito de timeout, 409, autenticação e
reconciliação, mas mantém o catálogo como fonte única de verdade. O fluxo não
fica atomicamente invisível entre serviços; por isso cada comando deve ser
consultável e cada objeto deve ter uma intenção persistente. Isso permite
retomar uploads e confirmações sem apagar um objeto confirmado nem criar versões
duplicadas.

## Evidência exigida

As tarefas posteriores devem testar replay, body hash divergente, fence antigo,
perda de resposta após commit, upgrade do schema V6 e preservação das APIs de
assets existentes. Nenhum bead pode fechar este ADR alegando atomicidade HTTP+SQL
ou testes que não foram executados.
