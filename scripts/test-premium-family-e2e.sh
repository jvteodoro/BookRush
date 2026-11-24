#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
run="bookrush-pf-$BASHPID-$RANDOM"
tmp=$(mktemp -d)
containers=()
cleanup() {
  if (( ${#containers[@]} )); then docker rm -fv "${containers[@]}" >/dev/null 2>&1 || true; fi
  docker network rm "$run" >/dev/null 2>&1 || true
  docker image rm "$run-subscription:local" "$run-family:local" >/dev/null 2>&1 || true
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
create keycloak -e KEYCLOAK_ADMIN=fixture-admin -e KEYCLOAK_ADMIN_PASSWORD=fixture-admin-only \
  quay.io/keycloak/keycloak:25.0 start-dev --hostname=http://keycloak:8080
start keycloak
create probe python:3.12-slim sleep infinity
docker cp scripts/premium-family-e2e.py "$run-probe:/tmp/premium-family-e2e.py"
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
  -e FAMILY_TOKEN_URL=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token \
  -e FAMILY_SUBSCRIPTION_CLIENT_SECRET=fixture-family-only "$run-family:local"
start family-service
run_probe wait http://family-service:8103/actuator/health
mkdir "$tmp/gateway"
cp infrastructure/traefik/{subscription,family}.yaml "$tmp/gateway/"
create gateway traefik:v3.3 --entrypoints.web.address=:8080 --providers.file.directory=/config
docker cp "$tmp/gateway" "$run-gateway:/config"
start gateway
run_probe wait http://gateway:8080/api/v1/subscription/me 401
run_probe commercial
subscription subscription-production billing-mock,production
run_probe production
docker restart "$run-family-service" >/dev/null
run_probe wait http://family-service:8103/actuator/health
docker stop "$run-subscription-service" >/dev/null
run_probe outage
docker exec "$run-postgres" psql -v ON_ERROR_STOP=1 -U postgres -d premium_e2e -Atc "
  SELECT 1 / CASE WHEN
    (SELECT count(*) FROM subscription.flyway_schema_history WHERE version IS NOT NULL AND success) = 4 AND
    (SELECT count(*) FROM family.flyway_schema_history WHERE version IS NOT NULL AND success) = 2
    THEN 1 ELSE 0 END" >/dev/null
echo 'Premium/Família E2E: gateway, Keycloak reconciliado 2x, Flyway após reinício e fail-closed OK'
