#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
run="bookrush-age-$BASHPID-$RANDOM"
temp=$(mktemp -d)
cleanup() {
  docker rm -fv "$run-probe" "$run-keycloak" >/dev/null 2>&1 || true
  docker network rm "$run" >/dev/null 2>&1 || true
  docker image rm "$run:local" >/dev/null 2>&1 || true
  rm -rf "$temp"
}
trap cleanup EXIT

mkdir "$temp/import"
python3 scripts/keycloak-age-profile-test.py fixture "$temp/import/bookrush-realm.json"
docker build -t "$run:local" -f infrastructure/keycloak/age-provider/Dockerfile .
docker network create "$run" >/dev/null
docker create --name "$run-keycloak" --network "$run" --network-alias keycloak \
  -e KEYCLOAK_ADMIN=fixture-admin -e KEYCLOAK_ADMIN_PASSWORD=fixture-only-password \
  "$run:local" start-dev --hostname=http://keycloak:8080 --import-realm >/dev/null
docker cp "$temp/import" "$run-keycloak:/opt/keycloak/data/import"
docker start "$run-keycloak" >/dev/null

docker create --name "$run-probe" --network "$run" \
  -e KEYCLOAK_AGE_PRODUCT_ISSUER=http://keycloak:8080/realms/bookrush \
  -e KEYCLOAK_AGE_ADMIN_URL=http://keycloak:8080 -e KEYCLOAK_CONFIG_CLI_ADMIN_USER=fixture-admin \
  -e KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD=fixture-only-password python:3.12-slim sleep infinity >/dev/null
docker cp scripts/keycloak-age-profile-test.py "$run-probe:/tmp/probe.py"
docker cp infrastructure/keycloak/reconcile-age-profile.py "$run-probe:/tmp/reconcile-age-profile.py"
docker cp infrastructure/keycloak/age "$run-probe:/tmp/age"
docker start "$run-probe" >/dev/null
probe() { docker exec "$run-probe" python /tmp/probe.py "$@"; }
probe before
docker exec "$run-probe" python /tmp/reconcile-age-profile.py
probe first
docker exec "$run-probe" python /tmp/reconcile-age-profile.py
probe after
