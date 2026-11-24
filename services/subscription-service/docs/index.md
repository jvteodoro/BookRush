# Subscription service

Owns the commercial plan catalog, subscription aggregate, transition audit
and outbox in schema `subscription`. Flyway V1 creates the tables, constraints
and pending outbox index. V2 seeds FREE v1 and PREMIUM v1. Use a dedicated
database role with ownership only of this schema; Flyway needs schema creation
or a schema pre-created by infrastructure. No other service reads this schema.

## Effective rights and state transitions

Unknown account contracts resolve to FREE v1. PREMIUM grants publishing, family
ownership and five additional seats. The service evaluates rights at query time
using a half-open UTC interval `[startsAt, endsAt)`. The result is commercial
only. Age, family member delegation and guardianship belong to other contexts.
The validity boundary applies even while the aggregate has yet to receive EXPIRE.
A consumer must check the deadline as well as the aggregate version.

| Command | Eligible starting state | Result |
|---|---|---|
| ACTIVATE | FREE, EXPIRED or CANCELED | ACTIVE with current Premium plan version and a current effective window |
| RENEW | ACTIVE | ACTIVE, same start and strictly later end |
| CANCEL_AT_END | ACTIVE | CANCEL_AT_END, rights retained through exclusive end |
| CANCEL_NOW | ACTIVE, CANCEL_AT_END or SUSPENDED | CANCELED, rights immediately false |
| SUSPEND | ACTIVE | SUSPENDED, rights immediately false |
| REACTIVATE | SUSPENDED or CANCEL_AT_END before end | ACTIVE in the same window |
| EXPIRE | Effectively expired given clock, not already recorded | EXPIRED |

Expired/canceled contracts require a new ACTIVATE. No grace period is applied.
Provider semantics, refunds, grace and restart terms remain product decisions.
The technical ingress receives normalized billing events, independent of provider.
A future adapter authenticates the provider and assigns contiguous per-owner
source versions. Gaps or stale changes return 409 without partial writes;
reconcile against authoritative billing and replay in order. Identical event
ID and payload return the original version. A new mutation, its transition
audit and EntitlementChanged outbox entry commit together under a row lock.
An explicit EXPIRE records the expiration event. Scheduling and dispatch of
outbox entries are deferred; reads enforce expiry without them.

## HTTP and identity

See the [OpenAPI contract](../api/openapi.yaml). Self routes use realm
`bookrush`, audience `bookrush-subscription`, scope `subscription.read`,
and derive issuer/subject from the validated JWT. Evaluation and ingestion
are private POST routes in the platform realm with the same audience,
respectively scope `entitlements.evaluate` and `subscription.transitions.write`
plus the allowlisted workload client ID in `azp`. JWT roles and scopes alone
never create an entitlement. Loss of storage returns 503, never a false
FREE snapshot. For browser routes only, configure exact origins and test the
Authorization preflight. Do not route `/api/internal/**` or actuator via the
product gateway.

The Keycloak fragments in `infrastructure/keycloak/subscription/` describe
opt-in registration for an isolated realm export. Merge scopes by name and
clients by clientId into the respective full realm documents before using the
existing declarative reconciler. Assign `subscription.read` to approved product
clients only. Inject confidential secrets from the protected environment when
rendering. Provision the transition client only when a trusted billing adapter or
disposable fixture actually needs it. No production realm reconciliation is
required to run this Bead's tests.

## Delivery and operations

Use the optional `infrastructure/compose.subscription.yaml` with the normal
Compose file and profile `subscription` in an isolated project. Supply schema
owner credentials through the environment. The profile's gateway listens on
loopback and routes only self reads. No service port is published.
Health and Prometheus are available on the internal network at port 8102.
Scrape `/actuator/prometheus` from that network. Diagnostics include transition
counts by operation/state, evaluations by entitlement/result and storage
failure counts. No identity values appear in metric labels.

The generic Jenkins validation job builds and tests the image using PostgreSQL
16. The delivery Bead `bookrush-7164` owns registration in integrated deploy
matrices, production routing/scrape, and deciding Kubernetes parity; the current
base Kustomization does not deploy either new context. Validate real issuer,
audience, scope, CORS and health through the intended gateway during that
rollout. Jenkins deploy for this isolated service is deliberately disabled.

Run `bash scripts/test-microservice.sh subscription-service` to reproduce
validation. The harness builds the Maven stage, tests clean and staged
Flyway migrations against a disposable PostgreSQL 16 database, and builds the
runtime image. The HTTP tests use local signed JWTs from a disposable JWKS,
exercising both realms, workload scopes, CORS and revocation without a live
identity provider.

## Verified billing delivery and reconciliation

Flyway V3 adds the durable billing inbox. A verified adapter calls the
private `POST /api/internal/v1/subscription/billing/events` using the
existing billing workload, same realm/audience and
`subscription.transitions.write` scope. The existing transition endpoint
remains compatible for trusted normalized commands; new financial
integrations should use the inbox. Neither route is on the gateway.

The provider boundary is the normalized delivery in OpenAPI. The adapter
must authenticate the original notification over its exact bytes using the
chosen provider protocol, bind the provider contract to a server-owned
identity, and confirm financial state from the trusted provider before
submitting a command. It must map external order to the next internal
subscription version. Never forward raw webhooks, customer identifiers,
payment instruments or financial payloads to the inbox. Use a stable
opaque external ID and a configured source namespace.

| Receipt | Operational behavior |
|---|---|
| 200 APPLIED | Transition, entitlement outbox and receipt committed together. Exact replays return the same event ID and state. |
| 200 STALE | A newer or equivalent commercial version already exists. No transition occurred. Reconcile against authoritative billing before submitting any new command. |
| 202 PENDING | Delivery is stored but its version has a gap. Compare currentVersion against verified history and resend the same delivery only after reconciliation. |
| 409 / 422 | Conflicting delivery or invalid lifecycle/window. Investigate and use verified corrective input with a fresh ID as appropriate. |
| 503 or transport timeout | Outcome may be uncertain. Retry the same verified event ID and normalized fields. A failed apply leaves the durable inbox pending and rolls back all commercial writes. |

Reconciliation is driven by the trusted adapter. It must read provider
state before filling gaps and avoid reissuing payments superseded by a
later cancellation or reversal. The service does not blindly reprocess
pending records after a gap, timeout or failed signature. Monitor
`bookrush.subscription.billing.deliveries` by bounded result. Pending
events can be investigated in chronological order through the inbox
index under operational access. Authentication credentials and payloads
are excluded from logs and metric labels. Inbox retention and the
provider replay horizon must be agreed before enabling deletion.

Provider selection, signature scheme/key rotation, checkout creation,
provider cancellation, refund/chargeback mapping, grace and retry policy
remain pending. No public real-payment endpoint or real provider implementation
is enabled in this revision.

## Mock-only educational prototype

Product chose simulated payments exclusively. Enable the simulator only in
an isolated environment: set `SUBSCRIPTION_MOCK_ENABLED=true`, Spring
profile `billing-mock` and `BOOKRUSH_ENVIRONMENT=local`, `development`
or `test`. For the opt-in Compose overlay set
`SUBSCRIPTION_SPRING_PROFILES=billing-mock`. Unknown and production
environment values disable both handlers. Active `prod` or
`production` profiles veto both handlers even if the other settings are
enabled. Keep the loopback gateway isolated and set explicit CORS origins.
The mock workload route is private and never routed by the gateway.

- `POST /api/v1/subscription/checkout` uses the authenticated product
  identity and scope `subscription.read`. Supply a UUID `Idempotency-Key`
  and no body. Approval starts a 30-day UTC period. The receipt always
  states `simulation: true` and `realPayment: false`.
- `POST /api/internal/v1/subscription/billing/mock/simulate` uses the existing
  allowlisted billing workload and `subscription.transitions.write`.
  Supply a UUID idempotency key and `{owner, scenario}`. This route cannot
  approve a checkout and is the only route for privileged scenarios.
- `DECLINE` is audited as a financial outcome without an entitlement
  transition. `RENEW` adds 30 days to an active window. `CANCEL` takes
  effect at the current exclusive deadline. `REFUND` revokes rights
  immediately and `CHARGEBACK` suspends immediately. There is no grace.
  These policies are provisional and apply to the prototype only.

V4 persists stable mock request identities and normalized commands; V3
processes benefit-changing events with the original audit and outbox.
Retry with the same key after an uncertain outcome. An exact retry reuses
the external event ID and time window, and a key reused for a different
scenario conflicts. Concurrent competing commands can return stale or
conflict; re-read state before starting a new command. The `mock` source
is reserved to the gated adapter. No amounts, real transactions or provider
credentials are recorded. Exclude `mock` source events and
`bookrush.subscription.mock.receipts` from any real-revenue reporting.
Provider signatures and financial production policies remain future work.
