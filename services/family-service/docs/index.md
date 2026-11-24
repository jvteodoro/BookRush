# Commercial families and invite reservations

This slice covers household creation, issuing and listing invitations,
cancellation, expiration and seat reservation. V1 creates household,
membership, permission, invitation, audit and outbox tables. V2 adds
recipient-issued invite addresses. The service owns schema `family`
and uses a dedicated database role. No database access to subscription
or Keycloak is required.

Membership acceptance, recipient refusal, member removal/suspension,
publication delegation writes and S2S membership queries belong to
`bookrush-5ine`. Its dependency on age classification remains in place.
No invite, address or commercial membership conveys legal authority,
history access or product access. The delegation column is commercial
state, which may not authorize publication on its own. The development
overlay must remain isolated pending product authorization integration.

## Authentication, lookup and entitlement

The [OpenAPI contract](../api/openapi.yaml) describes seven endpoints.
All use exact product issuer, JWT bearer audience `bookrush-family` and
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
At acceptance, the future writer must recheck current identity and
membership with the recipient's own bearer.

Creation and issuance use the real subscription S2S contract
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
- Creation serializes by technical identity. A future acceptance writer
  must acquire the same identity advisory lock before taking the household
  lock, reject an identity that owns a household, check the invitation and
  entitlement, then exchange the reservation for an active membership in
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
