# publisher-service

Publisher submissions and authorized metrics. Este serviço é um bounded context independente; contratos e ownership estão no ADR 0034.

Consulte [API](api.md) para submissões.

Uma submissão pode avançar para `SUBMITTED` usando `POST /api/v1/publisher/submissions/{id}/submit`.

Submission ownership is scoped to the authenticated principal. Anonymous
requests are rejected before any database write, and retrying a submit only
changes the caller's own submission.

The service validates the `bookrush-platform` issuer/JWK set and requires the
`bookrush-ingestion` audience. OAuth scopes and Keycloak groups are converted
to `SCOPE_*` and `ROLE_*` authorities before publisher/operator actions run.

Criação e submissão de publicação emitem fatos server-side ao behavior-service
com o bearer autenticado, chave idempotente e retry limitado. A telemetria é
best effort e não altera o ownership nem o estado transacional da submissão.

## Perfil local e healthcheck

Com `BOOKRUSH_SECURITY_ENABLED=false`, uma cadeia local explícita permite o
healthcheck do Compose sem bearer; o perfil protegido mantém a validação JWT,
issuer, audience e autorização das rotas de publicação.

## Identidade técnica e ownership

A identidade autoritativa é o par `identity_issuer`, `identity_subject`.
Em submissions, uploads e obras publicadas, o par identifica o autor.
Em rights_attestation, identifica quem fez a declaração. Quando o autor
declara explicitamente `claimsOwnRights`, o par também é projetado como
`holder_issuer`, `holder_subject`. Nos demais casos, o titular permanece
desconhecido. Toda nova declaração mantém a referência de evidência e
arquiva a anterior, inclusive seu par desconhecido quando aplicável.
Em publication_review, o par é do operador editorial. Operadores não são
aceitos como autores. O callback com segredo registra o identificador do
workload legado e deixa a autoria intacta.

A V8 preserva chaves, horários e relações existentes. Escritas novas mantêm
os campos legados para compatibilidade com o relatório existente e gravam
o par técnico. Leituras autorais usam exclusivamente o par técnico. Um par
conhecido é imutável. Um par nulo marca quarentena lógica. Ausência de
proveniência em um filho impede seu uso, mesmo quando a submissão já tem
autor conhecido.

### Procedimento de migração

1. Aplique a V8 por Flyway. A configuração histórica de um realm e um
   `subject_key` isolado não provam a origem de cada linha, portanto a V8
   não infere vínculo. Conte pares nulos por tabela para estimar o impacto.
2. Com acesso administrativo ao banco, revise uma fonte que correlacione o
   ID exato do registro ao issuer e subject validados no ato da gravação.
   Exemplos de evidência são um evento autenticado de auditoria que vincula
   o ID da operação ao par técnico. Coincidência de e-mail, nome, subject
   entre realms ou identidade atual do autor não basta.
3. Em uma transação, insira em `publisher.identity_evidence` o tipo
   (`entity`), chave original (`record_key`, UUID textual ou chave da
   membership), subject legado, issuer verificado, subject verificado e
   referência opaca da evidência. O subject verificado deve ser o mesmo
   registrado no legado. Rode `SELECT publisher.reconcile_legacy_identity();`
   na mesma transação, confira as atribuições esperadas e faça commit.
   A função ignora pares já preenchidos. Não injete tokens ou dados pessoais
   na referência. Registros inconclusivos permanecem em quarentena.
4. Revalide as linhas vinculadas de aprovação, upload e métricas. Um vínculo
   comprovado ao realm administrativo continua sendo daquele realm.
   Transferência entre realms exige uma decisão auditada posterior.
5. O corte autoral deve acompanhar `bookrush-rrnt` e o enforcement de publicação
   planejado. A chain atual continua exclusiva do realm de plataforma e as
   operações autorais já exigem a identidade de produto validada, portanto
   ficam fechadas pelo HTTP até a próxima entrega. Preservar colunas
   legadas permite consulta administrativa. Reabrir ownership por subject
   isolado em um rollback invalidaria esse controle.

### Limites transacionais

As mutações do autor bloqueiam a submissão após filtrar issuer e subject.
Upload e publicação ainda exigem o mesmo par na linha filha. O submit
reserva a transição em uma transação curta e libera o lock antes de chamar a
ingestão síncrona, que faz callback para a mesma submissão. Requisições
repetidas não disparam nova ingestão enquanto a submissão estiver enviada
ou em processamento. A revisão exige estado apropriado e vínculo de
catálogo confirmado. O callback serializa o vínculo e preserva o par do
autor. Métricas usam a obra publicada pertencente ao autor, sem aceitar o
ID de catálogo informado em um rascunho como prova. A retirada da obra
própria não consulta cobrança nem membership.

A membership local registra estado administrativo legado. A criação de
membership automática foi removida. Um registro ACTIVE não concede
publicação. `PublisherOwnership.resource` fornece ownership autoritativo ao
starter para a futura integração de PUBLISH_READ, PUBLISH_WRITE e
PUBLISH_WITHDRAW.

Validação local com PostgreSQL 16 e dois emissores Keycloak independentes.

```bash
bash scripts/test-publisher-ownership.sh
```
