#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
run="bookrush-pf-$BASHPID-$RANDOM"
tmp=$(mktemp -d)
containers=()
cleanup() {
  if (( ${#containers[@]} )); then docker rm -fv "${containers[@]}" >/dev/null 2>&1 || true; fi
  docker network rm "$run" >/dev/null 2>&1 || true
  docker image rm "$run-subscription:local" "$run-family:local" "$run-id:local" "$run-consumer:local" "$run-reader-state:local" "$run-reader-bff:local" "$run-catalog:local" "$run-book-content:local" "$run-storage:local" "$run-social:local" "$run-reader-profile:local" "$run-behavior:local" >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT
create() {
  local name=$1
  shift
  containers+=("$run-$name")
  docker create --name "$run-$name" --network "$run" --network-alias "$name" "$@" >/dev/null
}
start() { docker start "$run-$1" >/dev/null; }
run_probe() { docker exec "$run-probe" python /tmp/premium-family-e2e.py "$@"; }

python3 scripts/premium-family-e2e.py realms "$tmp"
docker network create "$run" >/dev/null
for service in subscription family; do
  docker build -t "$run-$service:local" -f "services/$service-service/Dockerfile" .
done

create postgres -e POSTGRES_PASSWORD=fixture-db-only -e POSTGRES_DB=premium_e2e postgres:16-alpine
start postgres
docker build -t "$run-id:local" -f infrastructure/keycloak/age-provider/Dockerfile .
create keycloak -e KEYCLOAK_ADMIN=fixture-admin -e KEYCLOAK_ADMIN_PASSWORD=fixture-admin-only \
  "$run-id:local" start-dev --hostname=http://keycloak:8080
start keycloak
create probe -e BOOKRUSH_E2E_COMMUNITY="${BOOKRUSH_E2E_COMMUNITY:-0}" -e BOOKRUSH_E2E_CONTENT="${BOOKRUSH_E2E_CONTENT:-0}" -e BOOKRUSH_E2E_READER_STATE="${BOOKRUSH_E2E_READER_STATE:-0}" -e KEYCLOAK_AGE_ADMIN_URL=http://keycloak:8080 \
  -e KEYCLOAK_AGE_PRODUCT_ISSUER=http://keycloak:8080/realms/bookrush \
  -e KEYCLOAK_CONFIG_CLI_ADMIN_USER=fixture-admin -e KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD=fixture-admin-only \
  python:3.12-slim sleep infinity
docker cp scripts/premium-family-e2e.py "$run-probe:/tmp/premium-family-e2e.py"
docker cp infrastructure/keycloak/reconcile-age-profile.py "$run-probe:/tmp/reconcile-age-profile.py"
docker cp infrastructure/keycloak/age "$run-probe:/tmp/age"
start probe
run_probe wait http://keycloak:8080/realms/master/.well-known/openid-configuration
for pass in 1 2; do
  create "reconcile-$pass" -e KEYCLOAK_URL=http://keycloak:8080 -e KEYCLOAK_USER=fixture-admin \
    -e KEYCLOAK_PASSWORD=fixture-admin-only -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
    -e IMPORT_FILES_LOCATIONS=/tmp/bookrush-*.json adorsys/keycloak-config-cli:6.3.0-18.0.2
  for realm in bookrush bookrush-platform; do
    docker cp "$tmp/$realm.json" "$run-reconcile-$pass:/tmp/bookrush-$realm.json"
  done
  docker start -a "$run-reconcile-$pass"
  run_probe identity "$pass"
done
docker exec "$run-probe" python /tmp/reconcile-age-profile.py
run_probe dates

db=(-e DB_HOST=postgres -e DB_NAME=premium_e2e -e DB_USERNAME=postgres -e DB_PASSWORD=fixture-db-only)
for i in {1..30}; do
  if docker exec "$run-postgres" pg_isready -q -h 127.0.0.1 -U postgres -d premium_e2e; then break; fi
  sleep 1
done
subscription() {
  create "$1" "${db[@]}" -e "SPRING_PROFILES_ACTIVE=$2" -e BOOKRUSH_ENVIRONMENT=test -e SUBSCRIPTION_MOCK_ENABLED=true \
    -e SUBSCRIPTION_PRODUCT_ISSUER=http://keycloak:8080/realms/bookrush \
    -e SUBSCRIPTION_PRODUCT_JWKS=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs \
    -e SUBSCRIPTION_WORKLOAD_ISSUER=http://keycloak:8080/realms/bookrush-platform \
    -e SUBSCRIPTION_WORKLOAD_JWKS=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs \
    -e SUBSCRIPTION_CORS_ORIGINS=http://browser.fixture "$run-subscription:local"
  start "$1"
  run_probe wait "http://$1:8102/actuator/health"
}
subscription subscription-service billing-mock
create family-service "${db[@]}" -e FAMILY_PRODUCT_ISSUER=http://keycloak:8080/realms/bookrush \
  -e FAMILY_PRODUCT_JWKS=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs \
  -e FAMILY_WORKLOAD_ISSUER=http://keycloak:8080/realms/bookrush-platform \
  -e FAMILY_WORKLOAD_JWKS=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs \
  -e FAMILY_TOKEN_URL=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token \
  -e FAMILY_IDENTITY_ADMIN_USERS=http://keycloak:8080/admin/realms/bookrush/users \
  -e FAMILY_IDENTITY_TOKEN_URL=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token \
  -e FAMILY_IDENTITY_CLIENT_SECRET=fixture-age-only -e SPRING_PROFILES_ACTIVE=supervision-mock \
  -e BOOKRUSH_ENVIRONMENT=test -e FAMILY_SUPERVISION_SIMULATION=true \
  -e 'FAMILY_PRODUCT_ACCESS_WORKLOADS=fixture-decision-reader=PRODUCT_USE:BOOK,SESSION_VIEW:SESSION,SUPERVISE_COMMENTS:COMMENT;bookrush-reader-state-s2s=PRODUCT_USE:READER_STATE,SUPERVISE_HISTORY:READING_HISTORY;bookrush-catalog-product-access-s2s=PRODUCT_USE:CATALOG,PRODUCT_USE:ASSET;bookrush-book-content-product-access-s2s=PRODUCT_USE:BOOK,PRODUCT_USE:ASSET;bookrush-social-product-access-s2s=PRODUCT_USE:COMMENT,SUPERVISE_COMMENTS:COMMENT;bookrush-reader-profile-product-access-s2s=PRODUCT_USE:PROFILE;bookrush-behavior-product-access-s2s=PRODUCT_USE:TELEMETRY' \
  -e FAMILY_SUBSCRIPTION_CLIENT_SECRET=fixture-family-only "$run-family:local"
start family-service
run_probe wait http://family-service:8103/actuator/health
docker build --target build -t "$run-consumer:local" -f services/family-service/Dockerfile .
create consumer-classes "$run-consumer:local" true
mkdir "$tmp/consumer"
docker cp "$run-consumer-classes:/workspace/target/test-classes/." "$tmp/consumer"
minor_subject="$(run_probe subject invitee-0)"
oidc=http://keycloak:8080/realms/bookrush-platform
create decision-consumer --entrypoint java \
  -e FAMILY_PRODUCT_ISSUER=http://keycloak:8080/realms/bookrush \
  -e FAMILY_PRODUCT_JWKS=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs \
  "$run-family:local" -Dloader.path=/tmp/consumer/ -Dloader.main=com.bookrush.family.CanonicalConsumer \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --server.port=8104 --fixture.minor-subject="$minor_subject" \
  --bookrush.security.product-access.enabled=true --bookrush.security.product-access.allow-insecure-http=true \
  --bookrush.security.product-access.decision-base-uri=http://family-service:8103 \
  --bookrush.security.product-access.workload-issuer="$oidc" \
  --bookrush.security.product-access.token-uri="$oidc/protocol/openid-connect/token" \
  --bookrush.security.product-access.jwk-set-uri="$oidc/protocol/openid-connect/certs" \
  --bookrush.security.product-access.client-id=fixture-decision-reader \
  --bookrush.security.product-access.client-secret=fixture-decision-only \
  --bookrush.security.product-access.request-timeout=5s
docker cp "$tmp/consumer" "$run-decision-consumer:/tmp/consumer"
start decision-consumer
run_probe wait http://decision-consumer:8104/actuator/health
if [[ ${BOOKRUSH_E2E_READER_STATE:-0} == 1 ]]; then
  for service in reader-state reader-bff; do
    docker build -t "$run-$service:local" -f "services/$service-service/Dockerfile" .
  done
  create event-sink python:3.12-slim python /tmp/premium-family-e2e.py event-sink
  docker cp scripts/premium-family-e2e.py "$run-event-sink:/tmp/premium-family-e2e.py"
  start event-sink
  product_security=(-e BOOKRUSH_SECURITY_ENABLED=true -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/bookrush
    -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-family
    -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs)
  state_access=(-e READER_STATE_PRODUCT_ACCESS_ENABLED=true -e READER_STATE_PRODUCT_ACCESS_ALLOW_INSECURE_HTTP=true
    -e READER_STATE_PRODUCT_ACCESS_ORIGIN=http://family-service:8103 -e READER_STATE_PRODUCT_ACCESS_WORKLOAD_ISSUER="$oidc"
    -e READER_STATE_PRODUCT_ACCESS_TOKEN_URI="$oidc/protocol/openid-connect/token"
    -e READER_STATE_PRODUCT_ACCESS_JWKS_URI="$oidc/protocol/openid-connect/certs"
    -e READER_STATE_PRODUCT_ACCESS_REQUEST_TIMEOUT=5s -e READER_STATE_PRODUCT_ACCESS_HUMAN_CLIENTS=fixture-product)
  create reader-state-service "${db[@]}" "${product_security[@]}" "${state_access[@]}" \
    -e READER_STATE_SUPERVISION_ENABLED=true -e READER_STATE_PRODUCT_ACCESS_CLIENT_SECRET=fixture-reader-state-only "$run-reader-state:local" --bookrush.behavior-url=http://event-sink:8888
  start reader-state-service
  run_probe wait http://reader-state-service:8094/actuator/health
  create reader-state-untrusted "${db[@]}" "${product_security[@]}" "${state_access[@]}" \
    -e READER_STATE_PRODUCT_ACCESS_CLIENT_ID=fixture-decision-reader \
    -e READER_STATE_PRODUCT_ACCESS_CLIENT_SECRET=fixture-decision-only "$run-reader-state:local" --bookrush.behavior-url=http://event-sink:8888
  start reader-state-untrusted
  run_probe wait http://reader-state-untrusted:8094/actuator/health
  create reader-bff-service "${product_security[@]}" \
    -e READER_BFF_SESSION_ENABLED=true -e READER_BFF_SESSION_ALLOW_INSECURE_HTTP=true \
    -e READER_BFF_SESSION_FAMILY_URL=http://family-service:8103 -e READER_BFF_SESSION_WORKLOAD_ISSUER="$oidc" \
    -e READER_BFF_SESSION_TOKEN_URL="$oidc/protocol/openid-connect/token" \
    -e READER_BFF_SESSION_JWKS="$oidc/protocol/openid-connect/certs" -e READER_BFF_SESSION_CLIENT_SECRET=fixture-session-only \
    -e READER_BFF_SUPERVISION_ENABLED=true \
    -e READER_BFF_SUPERVISED_COMMENTS_ENABLED="$([[ ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]] && echo true || echo false)" \
    -e READER_BFF_SUPERVISION_TOKEN_URI="$oidc/protocol/openid-connect/token" \
    -e READER_BFF_SUPERVISION_CLIENT_SECRET=fixture-reading-only -e READER_BFF_SUPERVISION_ALLOW_INSECURE_HTTP=true "$run-reader-bff:local"
  start reader-bff-service
  run_probe wait http://reader-bff-service:8101/actuator/health
  adult_subject="$(run_probe subject other)"
  docker exec -i "$run-postgres" psql -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -v "subject=$adult_subject" <<'SQL'
INSERT INTO reader_state.library(subject_key,book_id)
VALUES ('TechnicalIdentity[issuer=http://keycloak:8080/realms/bookrush, subject=' || :'subject' || ']',
        '8f21709f-9880-4689-9605-f941d355cc67');
SQL
fi
if [[ ${BOOKRUSH_E2E_CONTENT:-0} == 1 ]]; then
  for service in catalog book-content reader-bff; do
    docker build -t "$run-$service:local" -f "services/$service-service/Dockerfile" .
  done
  docker build -t "$run-storage:local" infrastructure/seaweedfs
  create seaweedfs -e STORAGE_ACCESS_KEY=content-fixture -e STORAGE_SECRET_KEY=content-fixture-only \
    -e STORAGE_MIN_FREE_SPACE=1MiB "$run-storage:local"
  start seaweedfs
  run_probe wait http://seaweedfs:8333/ 403
  python3 scripts/premium-family-e2e.py content-fixture "$tmp"
  for entry in 'books-public fixture.epub' 'books-processing fixture.txt'; do
    read -r bucket file <<< "$entry"
    docker run --rm --network "$run" --entrypoint curl curlimages/curl:8.10.1 \
      --fail --silent --show-error --aws-sigv4 'aws:amz:us-east-1:s3' -u content-fixture:content-fixture-only \
      -X PUT "http://seaweedfs:8333/$bucket"
    create "upload-$bucket" --entrypoint curl curlimages/curl:8.10.1 \
      --fail --silent --show-error --aws-sigv4 'aws:amz:us-east-1:s3' -u content-fixture:content-fixture-only \
      --upload-file /fixture "http://seaweedfs:8333/$bucket/$file"
    docker cp "$tmp/$file" "$run-upload-$bucket:/fixture"
    docker start -a "$run-upload-$bucket"
  done
  product_issuer=http://keycloak:8080/realms/bookrush
  authorize_content=(--bookrush.security.product-access.enabled=true --bookrush.security.product-access.allow-insecure-http=true
    --bookrush.security.product-access.decision-base-uri=http://family-service:8103
    --bookrush.security.product-access.token-uri="$oidc/protocol/openid-connect/token"
    --bookrush.security.product-access.jwk-set-uri="$oidc/protocol/openid-connect/certs"
    --bookrush.security.product-access.workload-issuer="$oidc"
    --bookrush.security.product-access.client-secret=fixture-content-only
    --bookrush.security.product-access.request-timeout=5s
    --bookrush.content-human-clients=fixture-product)
  create catalog-service "${db[@]}" -e STORAGE_ENABLED=true -e STORAGE_ENDPOINT=http://seaweedfs:8333 \
    -e STORAGE_PUBLIC_ENDPOINT=http://seaweedfs-s3-bookrush.jteodoro.tec.br \
    -e STORAGE_ACCESS_KEY=content-fixture -e STORAGE_SECRET_KEY=content-fixture-only \
    -e ASSET_ADMIN_TOKEN=disposable-catalog-token-123456789 \
    -e CATALOG_READER_INTERNAL_TOKEN=legacy-fixture \
    -e CATALOG_OIDC_ENABLED=true -e CATALOG_OIDC_ISSUER="$oidc" \
    -e CATALOG_OIDC_JWK_SET_URI="$oidc/protocol/openid-connect/certs" \
    -e CATALOG_PRODUCT_OIDC_ISSUER="$product_issuer" -e CATALOG_PRODUCT_OIDC_AUDIENCE=bookrush-family \
    -e CATALOG_PRODUCT_OIDC_JWK_SET_URI="$product_issuer/protocol/openid-connect/certs" "$run-catalog:local" \
    "${authorize_content[@]}" --bookrush.security.product-access.client-id=bookrush-catalog-product-access-s2s
  start catalog-service
  run_probe wait http://catalog-service:8080/actuator/health
  docker exec -i "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e \
    -v "epub_size=$(wc -c <"$tmp/fixture.epub")" -v "text_size=$(wc -c <"$tmp/fixture.txt")" \
    -v "epub_sha=$(sha256sum "$tmp/fixture.epub" | cut -d' ' -f1)" \
    -v "text_sha=$(sha256sum "$tmp/fixture.txt" | cut -d' ' -f1)" < scripts/content-distribution-fixture.sql
  create legacy-catalog "${db[@]}" -e STORAGE_ENABLED=true -e STORAGE_ENDPOINT=http://seaweedfs:8333 \
    -e STORAGE_PUBLIC_ENDPOINT=http://seaweedfs-s3-bookrush.jteodoro.tec.br \
    -e STORAGE_ACCESS_KEY=content-fixture -e STORAGE_SECRET_KEY=content-fixture-only \
    -e ASSET_ADMIN_TOKEN=disposable-catalog-token-123456789 "$run-catalog:local"
  start legacy-catalog
  run_probe wait http://legacy-catalog:8080/actuator/health
  product_auth=(-e BOOKRUSH_SECURITY_ENABLED=true -e BOOKRUSH_SECURITY_ISSUER="$product_issuer"
    -e BOOKRUSH_SECURITY_JWK_SET_URI="$product_issuer/protocol/openid-connect/certs" -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-family)
  create book-content-service "${product_auth[@]}" "$run-book-content:local" "${authorize_content[@]}" \
    --bookrush.security.product-access.client-id=bookrush-book-content-product-access-s2s
  start book-content-service
  run_probe wait http://book-content-service:8093/actuator/health
  if [[ ${BOOKRUSH_E2E_READER_STATE:-0} != 1 ]]; then
    create reader-bff-service "${product_auth[@]}" "$run-reader-bff:local"
    start reader-bff-service
    run_probe wait http://reader-bff-service:8101/actuator/health
  fi
fi
if [[ ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]]; then
  if [[ ${BOOKRUSH_E2E_CONTENT:-0} != 1 ]]; then
    product_issuer=http://keycloak:8080/realms/bookrush
    product_auth=(-e BOOKRUSH_SECURITY_ENABLED=true -e BOOKRUSH_SECURITY_ISSUER="$product_issuer"
      -e BOOKRUSH_SECURITY_JWK_SET_URI="$product_issuer/protocol/openid-connect/certs" -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-family)
    docker build -t "$run-catalog:local" -f services/catalog-service/Dockerfile .
    create catalog-service "${db[@]}" "$run-catalog:local"
    start catalog-service
    run_probe wait http://catalog-service:8080/actuator/health
    docker exec "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -c "
      INSERT INTO catalog.book(id,canonical_title,status)
      VALUES ('8f000000-0000-4000-8000-000000000001','Community fixture','ACTIVE')"
  fi
  authorize_community=(-e COMMUNITY_PRODUCT_ACCESS_ENABLED=true -e COMMUNITY_PRODUCT_ACCESS_ALLOW_INSECURE_HTTP=true
    -e COMMUNITY_PRODUCT_ACCESS_ORIGIN=http://family-service:8103 -e COMMUNITY_PRODUCT_ACCESS_WORKLOAD_ISSUER="$oidc"
    -e COMMUNITY_PRODUCT_ACCESS_TOKEN_URI="$oidc/protocol/openid-connect/token"
    -e COMMUNITY_PRODUCT_ACCESS_JWKS_URI="$oidc/protocol/openid-connect/certs"
    -e COMMUNITY_PRODUCT_ACCESS_CLIENT_SECRET=fixture-community-only -e COMMUNITY_PRODUCT_HUMAN_CLIENTS=fixture-product)
  for entry in 'behavior 8097' 'reader-profile 8095' 'social 8096'; do
    read -r service port <<< "$entry"
    docker build -t "$run-$service:local" -f "services/$service-service/Dockerfile" .
    create "$service-service" "${db[@]}" "${product_auth[@]}" "${authorize_community[@]}" \
      -e "COMMUNITY_PRODUCT_ACCESS_CLIENT_ID=bookrush-$service-product-access-s2s" \
      -e SOCIAL_SUPERVISED_COMMENTS_ENABLED="$([[ ${BOOKRUSH_E2E_READER_STATE:-0} == 1 ]] && echo true || echo false)" \
      -e SOCIAL_ADMIN_ISSUER="$oidc" -e SOCIAL_ADMIN_JWKS="$oidc/protocol/openid-connect/certs" \
      -e BOOKRUSH_S2S_SECURITY_ISSUER="$oidc" -e BOOKRUSH_S2S_SECURITY_JWK_SET_URI="$oidc/protocol/openid-connect/certs" \
      -e BEHAVIOR_MAINTENANCE_ENABLED=false "$run-$service:local"
    start "$service-service"
    run_probe wait "http://$service-service:$port/actuator/health"
  done
fi
mkdir "$tmp/gateway"
cp infrastructure/traefik/{subscription,family}.yaml "$tmp/gateway/"
if [[ ${BOOKRUSH_E2E_CONTENT:-0} == 1 || ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]]; then
  cp infrastructure/traefik/dynamic.yaml "$tmp/gateway/dynamic.yaml"
  # Preserve the commercial test routes when the general catalog router is present.
  sed -i '/entryPoints:/a\      priority: 220' "$tmp/gateway/subscription.yaml" "$tmp/gateway/family.yaml"
fi
if [[ ${BOOKRUSH_E2E_READER_STATE:-0} == 1 && ! -f "$tmp/gateway/dynamic.yaml" ]]; then
  {
    printf 'http:\n  routers:\n'
    awk '/^    reader-bff:$/ { copy=1 } copy { print } /^      service: reader-bff$/ { exit }' infrastructure/traefik/dynamic.yaml
    printf '  services:\n    reader-bff:\n      loadBalancer:\n        servers: [{url: "http://reader-bff-service:8101"}]\n'
  } > "$tmp/gateway/reading.yaml"
fi
create gateway -e BOOKRUSH_PROTECTED_DISTRIBUTION="$([[ ${BOOKRUSH_E2E_CONTENT:-0} == 1 ]] && echo true || echo false)" \
  traefik:v3.5.3 --entrypoints.web.address=:8080 --providers.file.directory=/config
docker cp "$tmp/gateway" "$run-gateway:/config"
start gateway
run_probe wait http://gateway:8080/api/v1/subscription/me 401
run_probe commercial
if [[ ${BOOKRUSH_E2E_READER_STATE:-0} == 1 ]]; then
  docker exec "$run-postgres" psql -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -Atc "
    SELECT 1 / CASE WHEN (SELECT count(*) FROM reader_state.session) = 2
      AND (SELECT count(*) FROM reader_state.session WHERE ended_at IS NULL) = 1
      AND (SELECT count(*) FROM reader_state.progress) = 1
      AND (SELECT count(*) FROM reader_state.bookmarks) = 1
      AND (SELECT count(*) FROM reader_state.library) = 2
      AND (SELECT count(*) FROM reader_state.seen_book) = 2 THEN 1 ELSE 0 END" >/dev/null
  window_start="$(run_probe reading-prepare)"
  docker exec -i "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e \
    -v "since=$window_start" -v "minor=$minor_subject" <<'SQL'
INSERT INTO reader_state.book_open(subject_key, book_id, request_id, occurred_at)
SELECT 'TechnicalIdentity[issuer=http://keycloak:8080/realms/bookrush, subject=' || :'minor' || ']',
       gen_random_uuid(), gen_random_uuid(), :'since'::timestamptz + delta
FROM (VALUES (interval '0.1 seconds'), (interval '0.2 seconds'), (interval '-1 day')) AS offsets(delta);
INSERT INTO reader_state.session(id,subject_key,started_at,ended_at,active_seconds)
SELECT gen_random_uuid(), 'TechnicalIdentity[issuer=http://keycloak:8080/realms/bookrush, subject=' || :'minor' || ']',
       :'since'::timestamptz + offset_start, :'since'::timestamptz + interval '1 second', 60
FROM (VALUES (interval '0.1 seconds'), (interval '-2 hours')) AS offsets(offset_start);
SQL
  run_probe supervised-reading
  docker exec "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -Atc "
    SELECT 1 / CASE WHEN count(*) FILTER (WHERE outcome=200)>0 AND count(*) FILTER (WHERE outcome=403)>0
    THEN 1 ELSE 0 END FROM reader_state.supervised_reading_audit" >/dev/null
  docker stop "$run-reader-state-service" >/dev/null
  run_probe reading-unavailable
  start reader-state-service
  run_probe wait http://reader-state-service:8094/actuator/health
  docker stop "$run-family-service" >/dev/null
  run_probe reading-unavailable
  run_probe bootstrap-unavailable
  start family-service
  run_probe wait http://family-service:8103/actuator/health
fi
if [[ ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]]; then
  adult_subject="$(run_probe subject other)"
  minor_subject="$(run_probe subject invitee-0)"
  docker exec -i "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e \
    -v "adult=$adult_subject" -v "minor=$minor_subject" <<'SQL'
SELECT 1 / CASE WHEN
  (SELECT count(*) FROM social.comments)=1 AND (SELECT count(*) FROM social.follows)=0
  AND (SELECT count(*) FROM social.book_rating)=1 AND (SELECT count(*) FROM social.likes)=0
  AND (SELECT count(*) FROM social.shares)=0 AND (SELECT count(*) FROM social.reports)=0
  AND (SELECT count(*) FROM behavior.events)=3 AND (SELECT count(*) FROM behavior.outbox)=3
  AND (SELECT count(*) FROM behavior.events WHERE event_key='community-fixture-adult'
       AND identity_subject LIKE '%subject=' || :'adult' || ']')=1
  AND (SELECT count(*) FROM behavior.events WHERE event_key='community-fixture-minor'
       AND identity_subject LIKE '%subject=' || :'minor' || ']')=1
  THEN 1 ELSE 0 END;
SQL
fi
if [[ ${BOOKRUSH_E2E_READER_STATE:-0} == 1 && ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]]; then
  start_at="$(run_probe comments-prepare)"
  unrelated="$(run_probe subject invitee-2)"
  docker exec -i "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e \
    -v "since=$start_at" -v "minor=$minor_subject" -v "unrelated=$unrelated" <<'SQL'
WITH fixtures AS (
  SELECT gen_random_uuid() AS id, 'visible-' || n AS body, :'minor' AS subject_key,
         :'since'::timestamptz + n * interval '1 millisecond' AS created_at, 'VISIBLE' AS visibility FROM generate_series(1,51) n
  UNION ALL
  SELECT gen_random_uuid(), body, CASE WHEN body='other' THEN :'unrelated' ELSE :'minor' END,
         :'since'::timestamptz + delta, visibility
  FROM (VALUES ('hidden','HIDDEN',interval '1 second'), ('removed','REMOVED',interval '1 second'),
               ('deleted','VISIBLE',interval '1 second'), ('other','VISIBLE',interval '1 second'),
               ('outside','VISIBLE',interval '-1 second')) AS extra(body,visibility,delta)
), inserted AS (
  INSERT INTO social.comments(id,subject_key,book_id,body,created_at,author_deleted_at)
  SELECT id, subject_key, '8f000000-0000-4000-8000-000000000001', body, created_at,
         CASE WHEN body='deleted' THEN clock_timestamp() END FROM fixtures
  RETURNING id
)
INSERT INTO social.comment_moderation_state(comment_id,visibility)
SELECT fixtures.id,visibility FROM fixtures JOIN inserted USING (id);
SQL
  run_probe supervised-comments
  docker exec "$run-postgres" psql -q -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -Atc "
    SELECT 1 / CASE WHEN (SELECT count(*) FROM social.activity)=1 AND (SELECT count(*) FROM behavior.events)=3
      AND EXISTS (SELECT 1 FROM social.supervised_comments_audit WHERE outcome=200 AND target_key IS NOT NULL
                  AND correlation_id='comments-audit-fixture')
      AND EXISTS (SELECT 1 FROM social.supervised_comments_audit WHERE outcome=403 AND target_key IS NOT NULL)
      THEN 1 ELSE 0 END" >/dev/null
fi
subscription subscription-production billing-mock,production
run_probe production
docker restart "$run-family-service" >/dev/null
run_probe wait http://family-service:8103/actuator/health
docker stop "$run-subscription-service" >/dev/null
run_probe outage
if [[ ${BOOKRUSH_E2E_CONTENT:-0} == 1 || ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]]; then
  docker stop "$run-family-service" >/dev/null
  run_probe content-evaluator-down
  if [[ ${BOOKRUSH_E2E_READER_STATE:-0} == 1 && ${BOOKRUSH_E2E_COMMUNITY:-0} == 1 ]]; then run_probe comments-unavailable; fi
fi
docker exec "$run-postgres" psql -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -Atc "
  SELECT 1 / CASE WHEN
    (SELECT count(*) FROM subscription.flyway_schema_history WHERE version IS NOT NULL AND success) = 4 AND
    (SELECT count(*) FROM family.flyway_schema_history WHERE version IS NOT NULL AND success) = 4
    THEN 1 ELSE 0 END" >/dev/null
echo 'Premium/Família E2E: gateway, Keycloak reconciliado 2x, Flyway após reinício e fail-closed OK'
