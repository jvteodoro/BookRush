# Commercial families and invite reservations

The service owns schema `family` and uses a dedicated database role.
V1 creates households, memberships, permissions, invitations, audit and
outbox. V2 adds recipient-issued invite addresses. V3 links the accepted
invitation to its membership and adds the membership version. Legacy
membership rows may have no invitation reference. Subscription and Keycloak
are accessed through their contracts, with no database access from Family.

Invite acceptance creates only the commercial membership, initially with
delegation disabled. It creates no product access, legal grant, supervision
or history access. Cross-service authorization and the identity fact adapter
are subsequent work. The development overlay remains isolated.

## Authentication, lookup and entitlement

The [OpenAPI contract](../api/openapi.yaml) describes 20 endpoints.
Browser-facing routes use exact product issuer, JWT bearer audience `bookrush-family` and
scope `family.manage` or `family.invites`. The service derives issuer
and subject from the validated bearer. A household UUID or invitation
UUID identifies a resource and cannot stand in for ownership. The
recipient view filters by both issuer and subject and exposes pending
unexpired invitations only. Read models omit user identifiers and ages.

The recipient authenticates and explicitly generates an opaque invite
address, then shares it with a prospective titular. Addresses expire
after 30 minutes and rotate on issue. The database stores a SHA-256
hash and the authenticated identity tuple, never the raw code. This is
proof of a recently authenticated identity, not verification of age,
custody or continued account status after issuance. There is no username
search, Admin API credential or public user directory. Unknown/expired
codes, self-invites and recipients already in a household yield the
same unavailable response. Do not log request bodies or invite codes.
Acceptance rechecks current identity and membership using the recipient's own bearer.

Creation, issuance, acceptance, reactivation and granting delegation use the real subscription S2S contract
`POST /api/internal/v1/subscription/entitlements/evaluate` with
`{owner:{issuer,subject},entitlement:"PREMIUM_FAMILY"}`. The caller
uses platform client_credentials, client `bookrush-family-s2s`,
audience `bookrush-subscription` and scope `entitlements.evaluate`.
Only an enabled evaluation with a future `effectiveUntil` can
authorize a new write. The caller does not cache entitlement.
Failed upstream authentication, an outage or invalid response yields
503. Caller authentication errors yield 401; missing scope or Premium
yields 403. Existing household reads, recipient reads, cancellation and
expiration remain available without Premium. A create replay does not
create a new resource, and an issue replay returns the original receipt.
Premium lapsed state does not delete memberships, audit or invitations.

## Capacity, consistency and expiration

- The owner is on the household row and never consumes a slot. The five
  additional slots count active/suspended memberships and unexpired
  PENDING invitations. Logical expiration is immediate at the server
  deadline, even before the background worker records EXPIRED.
- Transactional issuance, cancellation and expiration lock the household
  row. The worker claims bounded batches with row locks and SKIP LOCKED.
  Invitation issuance requires a UUID idempotency key scoped to the
  household. Same key and same input replay; same key and different
  input conflict. The database prohibits two PENDING rows for a recipient
  in the same household. Cancellation is idempotent.
- Creation serializes by technical identity. The acceptance writer
  acquires the same identity advisory lock before taking the household
  lock, rejects an identity that owns a household, checks the invitation and
  entitlement, then exchanges the reservation for an active membership in
  the same database transaction. The partial unique membership index
  enforces a single active or suspended membership per identity.
- Reads and capacity track an exclusive expiration limit; invites last
  seven days. A slot becomes available immediately on cancel or expiry.
  Expiry batches write a single durable event per transition. Household
  creation and invitation state writes roll back with audit and outbox
  if any write fails. Consume events by immutable eventId and use the
  aggregate version for ordering. The dispatcher and inbox owners
  integrate this contract in later delivery work.

## Local and delivery setup

Run `bash scripts/test-microservice.sh family-service` for a local Jenkins
equivalent using a PostgreSQL 16 disposable database. The suite also
uses signed product tokens and tests the outbound client against an
HTTP fixture of the subscription contract, including token exchange,
revocation and failure. It does not claim production identity or gateway
validation.

For local Compose add `infrastructure/compose.subscription.yaml` and
`infrastructure/compose.family.yaml` with profiles `subscription` and
`family`. Configure separate schema owners and inject
`SUBSCRIPTION_FAMILY_CLIENT_SECRET` through the protected environment.
Merge the declarative product scope fragment into the full test realm
before reconcile, and assign the scopes to approved product clients.
The subscription fragment already provides the platform workload.
The optional family Traefik listener is bound to loopback on port 18103.
Health and Prometheus are internal on 8103. The subscription dependency
failure metric has no identity labels. Production routing/scrape and
Kubernetes parity remain in `bookrush-7164`.


## Membership controls and authorization facts

The recipient can accept or decline their own live invitation using
`family.invites`, including with restricted product access. Acceptance
locks the recipient identity before the household, rechecks both the
invitation deadline and the current Premium deadline, and stores the
membership, default permission, terminal invitation state and two ordered
events in one transaction. A replay returns the original membership ID,
including after removal, and performs no writes. Decline releases the
reservation and can be replayed even after the holder loses Premium.

The holder manages members using `family.manage`. Reads return the
membership `version` and delegation `permissionVersion`. Writes compare
the appropriate `expectedVersion`, and a replay of the immediately
preceding completed state returns success without emitting another event.
Suspension retains the seat and the stored delegation, withholding
commercial eligibility while suspended. Reactivation checks current
entitlements. Removal clears any grant, frees the seat and preserves
the historical membership. A removed membership cannot be reactivated.
A later membership requires a new invitation and starts with delegation
disabled. Removing, suspending or revoking privileges remains possible
when Premium is inactive.

The private `POST /api/internal/v1/family/membership/evaluate` verifies
the platform issuer, audience `bookrush-family`, scope
`family.membership.read` and allowlisted `azp`
`bookrush-product-access-s2s`. Its request contains the technical product
identity as resolved by the authorized consumer. The response distinguishes
ABSENT, ACTIVE and SUSPENDED. An active membership can carry the holder's
current Premium deadline. Missing or unavailable entitlements cannot
produce new rights. The lookup returns 503 if an active membership needs
a dependency that is unavailable. Suspended or absent facts need no
subscription lookup.

`delegationEnabled` is a stored fact. A consumer must also establish
ACTIVE, a still-valid holder deadline and eligible adulthood from the
current trusted `AgePolicy.Source` before authorizing publication.
A minor, unknown age or insufficient verification fails the final decision.
The response contains no effective publication authorization, personal
data or supervision state. The consumer must not cache beyond the earliest
known deadline or reuse a stale allow after revocation.

For isolated reconcile, add the family platform fragment to the full
platform realm, injecting `FAMILY_MEMBERSHIP_CLIENT_SECRET` from protected
environment. The workload has the dedicated read scope and no realm
administration permissions. The gateway continues exposing only
`/api/v1/family` and its public subpaths. The committed-event counter uses
only a bounded event type label.

## Academic supervision and current product facts

V4 adds supervision grants, idempotent command receipts, private audit
and an outbox to the family schema. A grant binds an exact adult–minor
pair. The public product client needs `supervision.request` to submit an
owned request or list its own grants. The requester and target are checked
using current identity facts. The request begins as REQUESTED and
membership acceptance never creates a grant.

The isolated product realm fragment `identity-reader.fragment.json`
sets up the confidential `bookrush-family-age-s2s` client with
`realm-management:view-users`, the Keycloak permission for read-only
user details. It receives no user edit permissions. The adapter calls
only exact-subject GETs at the configured internal Admin API, verifies the
returned ID and uses the live enabled flag and declared date via
`AgePolicy`. No dates, documents or age claims are persisted in Family.
Declaration is sufficient for these academic product and scope decisions.
Physical verification of age is a separate future fact source.

Administrative command handling requires `supervision.manage`, the
`bookrush-family-supervision-admin` audience and the dedicated platform
client. Enable only with `FAMILY_SPRING_PROFILES=supervision-mock`,
`FAMILY_SUPERVISION_SIMULATION=true` and a local, development or test
`BOOKRUSH_ENVIRONMENT`. A production profile vetoes administrative
simulation. The recipient's issuer and subject, guardian identity,
key and expected version are required. APPROVE sets explicit requested
scopes and an exact non-retroactive window, ending no later than majority.
Expansion requires a fresh APPROVE with a new opaque verification UUID
and a new window. REDUCE preserves the existing window and cannot add
scopes. Suspension and contestation close scopes immediately. Rejection,
revocation and expiry are terminal. If competing mutations race, the
loser must reload the version before submitting a new command. A retry
with the same key returns its receipt and never makes another change.

The internal `supervision.evaluate` scope is attached only to the
allowlisted decision workload. The two queries project current facts
for future `product-access-v1` integration. They use the v1 denial
reasons, the scope/grant ID and version, and validity bounds. They do
not implement the shared decision endpoint or authorize other services
on their own. Consumers must derive the guardian or actor from the
authenticated request and resolve the exact target. Both queries are
private, outside the Traefik public routes.

- Product eligibility for a minor requires current eligible age,
  ACTIVE membership, a live holder entitlement and an effective
  PRODUCT_ACCESS grant from an eligible adult. An adult needs no
  membership. A missing mandatory dependency produces 503.
- Legal scope checks do not call Subscription. They validate the exact
  pair and scope, live grant and current minor age. The permitted
  history window is `[max(grant start, now - 90 days), now)`, constrained
  by the grant and the civil cutoff. The check stops at majority even if
  the database state or JWT has not been refreshed. Responses do not
  permit caching allows across requests. Actual product and history
  enforcement remains downstream.

Audit records contain opaque actor/target references, event, simulation
marker, version and timestamp. The maintenance job uses bounded batches
and removes audit entries at 180 days. The outbox carries only the
opaque aggregate ID, version and technical event data, independently of
audit retention. Future history services must audit actual data reads
when they introduce those operations. Metrics use only fixed event and
dependency labels. Use secrets from the isolated environment for the
identity reader and administrative client. Real verification, history
delivery and general API blocking remain outside this delivery.

## Avaliador canônico de autorização de produto v1

`POST /api/internal/v1/product-access/decisions` recebe um JWT técnico do
realm de plataforma com audience `bookrush-family` e scope
`product-access.evaluate`. O consumidor valida a sessão humana, sua
audience de rota, scopes e ownership antes de declarar o ator e o recurso.
O family-service confia nessa declaração apenas para workloads autorizados.
Uma decisão de ownership sobre recurso externo usa a identidade resolvida
pelo consumidor. Em `PRODUCT_USE`, o consumidor preenche `resource.owner`
apenas quando a operação exige acesso ao próprio recurso. O dono continua
aplicando suas restrições locais e de
escrita transacional.

Configure `FAMILY_PRODUCT_ACCESS_WORKLOADS` no formato
`cliente=ACAO:RECURSO,ACAO:RECURSO;outro=ACAO:RECURSO`. Vazio deixa todos
os workloads sem acesso. Atribua o scope declarativo
`product-access.evaluate` a clientes confidenciais individuais do realm
de plataforma. A configuração de produção deve ser preparada junto à
adoção de cada consumidor. Exemplos de conjuntos mínimos:

| Consumidor | Pares de ação e recurso |
|---|---|
| Sessão/BFF | `SESSION_VIEW:SESSION` e apenas os pares de leitura que o BFF efetivamente aplica |
| Estado de leitura | `PRODUCT_USE:READER_STATE` |
| Publicador | `PUBLISH_READ:SUBMISSION,PUBLISH_WRITE:SUBMISSION,PUBLISH_WITHDRAW:SUBMISSION` |
| Família | `INVITE_SELF:INVITATION,FAMILY_MANAGE:FAMILY` |
| Projeções supervisionadas | `SUPERVISE_HISTORY:READING_HISTORY` ou `SUPERVISE_COMMENTS:COMMENT`, conforme a projeção |

`FAMILY_MANAGE` usa `resource.id` definido pelo backend. `CREATE` não
recebe um identificador. As demais operações usam
`OPERAÇÃO:<uuid-da-família>`. `INVITE`, `REACTIVATE` e
`GRANT_PUBLICATION` exigem Premium vigente. `READ`, `CANCEL`, `REMOVE`,
`SUSPEND` e `REVOKE_PUBLICATION` preservam as operações do titular após
o vencimento. O avaliador confirma a titularidade da família no banco local.

O histórico aceita `books-accessed` e `reading-activity`. Comentários usam
`COMMENT`; ambos exigem o alvo exato em `resource.owner`. O endpoint v1
autoriza o escopo e respeita a vigência e a maioridade. Para entregar fatos
de leitura ou comentários, o serviço proprietário deverá obter também
`windowFrom` e `windowUntil` da API S2S de fatos de supervisão e aplicar a
janela e a visibilidade em sua consulta.

A decisão usa o relógio do servidor e expira em, no máximo, cinco segundos,
ou no primeiro vencimento conhecido. `factVersions` inclui apenas versões
disponíveis utilizadas na avaliação. A revogação é consultada em toda nova
operação e na reavaliação final de escritas sensíveis. O gateway público
continua expondo apenas operações públicas declaradas.

O protótipo usa a proveniência declarada do Keycloak. A opção
`FAMILY_PRODUCT_ACCESS_AGE_VERIFICATION_REQUIRED` permite exigir evidência
verificada para elegibilidade de produto e publicação, com resultado
restrito enquanto a fonte ainda só oferecer declarações. As rotas mínimas
continuam consultando o estado da conta.

`POST /api/internal/v1/product-access/profile-discovery` entrega fatos
efêmeros de descoberta para os workloads específicos de social e perfil.
O endpoint usa a mesma credencial de plataforma avaliada no starter,
consulta uma lista atual e limitada do Keycloak e devolve apenas subjects
solicitados que atendem à regra etária. O contrato de decisão v1 permanece
responsável por autorizar as operações do ator autenticado.


O endpoint interno `POST /api/internal/v1/family/supervision/history-window`
permite ao reader-state resolver o UUID de uma concessão do ator e consultar
a janela de um dos dois escopos de leitura. A identidade do ator provém do
JWT validado pelo reader-state; o token técnico do reader-state limita a
fronteira de confiança. O endpoint retorna somente identidade técnica do alvo
e fatos da concessão. Os dados de leitura ficam no banco do reader-state.

O mesmo endpoint interno de resolução de janela aceita COMMENTS somente do
workload autorizado do social. O leitor continua limitado aos dois escopos
de histórico. Resolver a janela é uma operação independente de cobrança.
