#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
suffix="$RANDOM-$$"
network="bookrush-book-ratings-$suffix"
postgres="$network-postgres"
keycloak="$network-keycloak"
service="$network-service"
gateway="$network-gateway"
probe="$network-probe"
image="bookrush/book-ratings-e2e:$suffix"
catalog_image="bookrush/ratings-catalog-e2e:$suffix"
catalog="$network-catalog"
test_image="bookrush/book-ratings-test:$suffix"
work_dir="$(mktemp -d)"
cleanup() {
  local result=$?
  if (( result != 0 )); then
    for container in "$service" "$catalog" "$keycloak" "$gateway"; do docker logs --tail 80 "$container" >&2 || true; done
  fi
  docker rm -fv "$probe" "$gateway" "$catalog" "$service" "$keycloak" "$postgres" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  docker image rm "$image" "$test_image" "$catalog_image" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$result"
}
trap cleanup EXIT

docker build --target build -q -t "$test_image" -f services/social-service/Dockerfile . >/dev/null
docker run --rm "$test_image" mvn -B verify
docker build -q -t "$image" -f services/social-service/Dockerfile . >/dev/null
docker build -q -t "$catalog_image" -f services/catalog-service/Dockerfile . >/dev/null
docker network create "$network" >/dev/null
docker run -d --name "$postgres" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=bookrush -e POSTGRES_USER=bookrush -e POSTGRES_PASSWORD=disposable postgres:16-alpine >/dev/null
for attempt in {1..60}; do
  if docker exec "$postgres" pg_isready -h 127.0.0.1 -U bookrush -d bookrush >/dev/null 2>&1; then break; fi
  sleep 1
done
# Upgrade an existing V9 social schema without modifying legacy data.
docker exec "$postgres" createdb -U bookrush upgrade
for version in {1..9}; do
  for migration in services/social-service/src/main/resources/db/migration/V"${version}"__*.sql; do
    docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade < "$migration" >/dev/null
  done
done
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade -c "INSERT INTO social.likes(subject_key,book_id) VALUES ('legacy','11111111-1111-4111-8111-111111111111'); INSERT INTO social.follows(follower,followed) VALUES ('legacy','other');" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade < services/social-service/src/main/resources/db/migration/V10__book_ratings.sql >/dev/null
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT count(*) FROM social.likes WHERE subject_key='legacy'")" == 1 ]]
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT count(*) FROM social.follows WHERE follower='legacy'")" == 1 ]]
if docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade -c "INSERT INTO social.book_rating(subject_key,book_id,stars) VALUES ('bad','11111111-1111-4111-8111-111111111111',6)" >/dev/null 2>&1; then
  echo 'Rating constraint accepted invalid stars' >&2; exit 1
fi
cp scripts/fixtures/reader-test-realm.json "$work_dir/realm.json"

docker create --name "$keycloak" --network "$network" --network-alias keycloak \
  -e JAVA_OPTS_APPEND="-Xms128m -Xmx512m" quay.io/keycloak/keycloak:25.0 start-dev --import-realm >/dev/null
chmod 755 "$work_dir"
docker cp "$work_dir/." "$keycloak:/opt/keycloak/data/import" >/dev/null
docker start "$keycloak" >/dev/null
docker run -d --name "$service" --network "$network" --network-alias social-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=disposable \
  -e BOOKRUSH_CORS_ALLOWED_ORIGINS=https://bookrush.jteodoro.tec.br -e CATALOG_URL=http://catalog-service:8080 \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/search-tests \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/search-tests/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=account "$image" >/dev/null
docker run -d --name "$catalog" --network "$network" --network-alias catalog-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=disposable \
  -e CATALOG_OIDC_ENABLED=true \
  -e CATALOG_OIDC_ISSUER=http://keycloak:8080/realms/search-tests \
  -e CATALOG_OIDC_JWK_SET_URI=http://keycloak:8080/realms/search-tests/protocol/openid-connect/certs \
  -e CATALOG_OIDC_AUDIENCE=platform-only "$catalog_image" >/dev/null
# Wait for catalog migrations before seeding two approved catalogue entries.
for attempt in {1..180}; do
  if docker exec "$postgres" psql -At -U bookrush -d bookrush -c "SELECT count(*) FROM catalog.book" >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush -c "INSERT INTO catalog.book(id,canonical_title,status) VALUES ('11111111-1111-4111-8111-111111111111','Rating fixture','ACTIVE'),('22222222-2222-4222-8222-222222222222','Other book','ACTIVE')" >/dev/null
# Use the versioned gateway routes, not a direct service-only smoke test.
docker create --name "$gateway" --network "$network" --network-alias gateway traefik:v3.5.3 \
  --providers.docker=false --providers.file.filename=/dynamic.yaml \
  --providers.file.watch=false --entrypoints.web.address=:80 >/dev/null
docker cp infrastructure/traefik/dynamic.yaml "$gateway:/dynamic.yaml" >/dev/null
docker start "$gateway" >/dev/null
docker run -d --name "$probe" --network "$network" python:3.12-slim sleep 600 >/dev/null
docker exec -i "$probe" python3 - < scripts/test-book-ratings-e2e.py
# The second boot must validate previously applied Flyway migrations.
docker restart "$service" >/dev/null
docker exec -i "$probe" python3 - <<'PY'
import time, urllib.request
for attempt in range(120):
    try:
        with urllib.request.urlopen('http://social-service:8096/actuator/health', timeout=2) as response:
            if response.status == 200: break
    except OSError: pass
    time.sleep(1)
else: raise SystemExit('Social restart/Flyway validation failed')
print('Book ratings E2E: restart and Flyway validation OK')
PY
docker exec -i "$probe" python3 - --restart < scripts/test-book-ratings-e2e.py
docker stop "$catalog" >/dev/null
docker exec -i "$probe" python3 - --catalog-down < scripts/test-book-ratings-e2e.py
