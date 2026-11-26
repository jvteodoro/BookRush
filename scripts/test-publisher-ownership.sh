#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
pg=
keycloaks=()
cleanup() {
  if [[ -n $pg ]]; then docker rm -fv "$pg" >/dev/null 2>&1 || true; fi
  if (( ${#keycloaks[@]} )); then docker rm -fv "${keycloaks[@]}" >/dev/null 2>&1 || true; fi
  rm -rf -- "$work"
}
trap cleanup EXIT

tar -cf - --exclude=target platform services/publisher-service | tar -xf - -C "$work"
pg=$(docker run -d -p 127.0.0.1::5432 -e POSTGRES_USER=bookrush_test   -e POSTGRES_PASSWORD=test-only-not-for-production -e POSTGRES_DB=publisher_upgrade postgres:16-alpine)
for _ in {1..45}; do
  docker exec "$pg" pg_isready -q -h 127.0.0.1 -U bookrush_test && break
  sleep 1
done
docker exec "$pg" createdb -U bookrush_test publisher_clean
database=$(docker port "$pg" 5432/tcp)
export TEST_DATABASE_URL="jdbc:postgresql://$database/publisher_upgrade"
export TEST_DATABASE_CLEAN_URL="jdbc:postgresql://$database/publisher_clean"

mkdir "$work/realms"
python3 - "$work/realms" <<'PY'
import json
from pathlib import Path
import sys

folder = Path(sys.argv[1])
for realm, client, operator in (("bookrush", "bookrush-web", False),
                                ("bookrush-platform", "bookrush-editorial", True)):
    user = {
        "id": "c345dca4-981c-4310-9bf1-cb902421c294",
        "username": "shared-name", "enabled": True, "firstName": "Fixture", "lastName": "Author",
        "email": "same@example.invalid", "emailVerified": True,
        "credentials": [{"type": "password", "value": "fixture-only", "temporary": False}],
    }
    if operator:
        user["groups"] = ["/operators"]
    representation = {
        "realm": realm, "enabled": True, "sslRequired": "none",
        "groups": [{"name": "operators"}] if operator else [],
        "users": [user],
        "clients": [{
            "clientId": client, "publicClient": True, "directAccessGrantsEnabled": True,
            "defaultClientScopes": ["basic", "profile", "email"],
            "protocolMappers": [{
                "name": "groups", "protocol": "openid-connect", "protocolMapper": "oidc-group-membership-mapper",
                "config": {"claim.name": "groups", "access.token.claim": "true", "full.path": "false"},
            }],
        }],
    }
    (folder / f"{realm}.json").write_text(json.dumps(representation))
PY

# Keycloak user IDs are unique across realms within one installation.
# Independent installations prove the same subject can exist in two issuers.
start_realm() {
  local realm=$1 variable=$2 container origin discovery
  container=$(docker create -p 127.0.0.1::8080 quay.io/keycloak/keycloak:25.0.6 start-dev --import-realm)
  keycloaks+=("$container")
  mkdir "$work/$realm"
  cp "$work/realms/$realm.json" "$work/$realm/$realm-realm.json"
  docker cp "$work/$realm" "$container:/opt/keycloak/data/import"
  docker start "$container" >/dev/null
  origin="http://$(docker port "$container" 8080/tcp)"
  discovery="$origin/realms/$realm/.well-known/openid-configuration"
  for _ in {1..90}; do
    curl -fsS --max-time 2 "$discovery" >/dev/null 2>&1 && break
    sleep 1
  done
  curl -fsS --max-time 2 "$discovery" >/dev/null
  export "$variable=$origin"
}
start_realm bookrush TEST_KEYCLOAK_ORIGIN
start_realm bookrush-platform TEST_KEYCLOAK_PLATFORM_ORIGIN

cd "$work"
mvn -B -f platform/pom.xml install -DskipTests
mvn -B -f services/publisher-service/pom.xml test
mvn -B -f services/publisher-service/pom.xml -Dtest=PublisherMetricsPostgresIT test
