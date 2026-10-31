#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
suffix="$RANDOM-$$"
network="bookrush-profile-search-$suffix"
postgres="$network-postgres"
keycloak="$network-keycloak"
service="$network-service"
gateway="$network-gateway"
probe="$network-probe"
image="bookrush/profile-search-e2e:$suffix"
test_image="bookrush/profile-search-test:$suffix"
work_dir="$(mktemp -d)"
cleanup() {
  local result=$?
  if (( result != 0 )); then
    for container in "$service" "$keycloak" "$gateway"; do docker logs --tail 80 "$container" >&2 || true; done
  fi
  docker rm -fv "$probe" "$gateway" "$service" "$keycloak" "$postgres" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  docker image rm "$image" "$test_image" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$result"
}
trap cleanup EXIT

docker build --target build -q -t "$test_image" -f services/reader-profile-service/Dockerfile . >/dev/null
docker run --rm "$test_image" mvn -B verify
docker build -q -t "$image" -f services/reader-profile-service/Dockerfile . >/dev/null
docker network create "$network" >/dev/null
docker run -d --name "$postgres" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=bookrush -e POSTGRES_USER=bookrush -e POSTGRES_PASSWORD=disposable postgres:16-alpine >/dev/null
for attempt in {1..60}; do
  if docker exec "$postgres" pg_isready -h 127.0.0.1 -U bookrush -d bookrush >/dev/null 2>&1; then break; fi
  sleep 1
done
# Preserve legacy rows and privacy through an upgrade from V3.
docker exec "$postgres" createdb -U bookrush upgrade
for migration in services/reader-profile-service/src/main/resources/db/migration/V{1,2,3}__*.sql; do
  docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade < "$migration" >/dev/null
done
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade -c "INSERT INTO reader_profile.profile(subject_key,display_name,is_public) VALUES ('legacy','Legacy',false); INSERT INTO reader_profile.profile_privacy(subject_key,profile_visibility) VALUES ('legacy','PRIVATE');" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade < services/reader-profile-service/src/main/resources/db/migration/V4__profile_search_username.sql >/dev/null
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT display_name||':'||is_public||':'||profile_visibility FROM reader_profile.profile JOIN reader_profile.profile_privacy USING(subject_key) WHERE subject_key='legacy' AND username IS NULL")" == Legacy:false:PRIVATE ]]

cat > "$work_dir/realm.json" <<'JSON'
{"realm":"search-tests","enabled":true,"clients":[{"clientId":"search-web","publicClient":true,"directAccessGrantsEnabled":true,"protocol":"openid-connect","protocolMappers":[{"name":"subject","protocol":"openid-connect","protocolMapper":"oidc-sub-mapper","config":{"access.token.claim":"true"}},{"name":"username","protocol":"openid-connect","protocolMapper":"oidc-usermodel-property-mapper","config":{"user.attribute":"username","claim.name":"preferred_username","jsonType.label":"String","access.token.claim":"true"}},{"name":"full name","protocol":"openid-connect","protocolMapper":"oidc-full-name-mapper","config":{"access.token.claim":"true"}},{"name":"audience","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper","config":{"included.custom.audience":"account","access.token.claim":"true"}}]}],"users":[{"username":"ana.reader","email":"ana@example.test","emailVerified":true,"enabled":true,"firstName":"Ana","lastName":"Leitora","credentials":[{"type":"password","value":"disposable-pass","temporary":false}]},{"username":"bruno.reader","email":"bruno@example.test","emailVerified":true,"enabled":true,"firstName":"Bruno","lastName":"Leitor","credentials":[{"type":"password","value":"disposable-pass","temporary":false}]}]}
JSON
docker create --name "$keycloak" --network "$network" --network-alias keycloak \
  -e JAVA_OPTS_APPEND="-Xms128m -Xmx512m" quay.io/keycloak/keycloak:25.0 start-dev --import-realm >/dev/null
chmod 755 "$work_dir"
docker cp "$work_dir/." "$keycloak:/opt/keycloak/data/import" >/dev/null
docker start "$keycloak" >/dev/null
docker run -d --name "$service" --network "$network" --network-alias reader-profile-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=disposable \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/search-tests \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/search-tests/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=account "$image" >/dev/null
# Use the versioned gateway routes, not a direct service-only smoke test.
docker create --name "$gateway" --network "$network" --network-alias gateway traefik:v3.5.3 \
  --providers.docker=false --providers.file.filename=/dynamic.yaml \
  --providers.file.watch=false --entrypoints.web.address=:80 >/dev/null
docker cp infrastructure/traefik/dynamic.yaml "$gateway:/dynamic.yaml" >/dev/null
docker start "$gateway" >/dev/null
docker run -d --name "$probe" --network "$network" python:3.12-slim sleep 600 >/dev/null
docker exec -i "$probe" python3 - < scripts/test-profile-search-e2e.py
# The second boot must validate previously applied Flyway migrations.
docker restart "$service" >/dev/null
docker exec -i "$probe" python3 - <<'PY'
import time, urllib.request
for attempt in range(60):
    try:
        with urllib.request.urlopen('http://reader-profile-service:8095/actuator/health', timeout=2) as response:
            if response.status == 200: break
    except OSError: pass
    time.sleep(1)
else: raise SystemExit('Profile restart/Flyway validation failed')
print('Profile search E2E: restart and Flyway validation OK')
PY
