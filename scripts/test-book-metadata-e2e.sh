#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
suffix="$RANDOM-$$"
network="bookrush-book-metadata-$suffix"
postgres="$network-postgres"
keycloak="$network-keycloak"
state="$network-state"
bff="$network-bff"
catalog="$network-catalog"
gateway="$network-gateway"
probe="$network-probe"
work_dir="$(mktemp -d)"
services=(reader-state-service reader-bff-service catalog-service)
images=()
for service in "${services[@]}"; do images+=("bookrush/metadata-$service:$suffix" "bookrush/metadata-$service-test:$suffix"); done
cleanup() {
  local result=$?
  if (( result != 0 )); then
    for container in "$state" "$bff" "$catalog" "$keycloak" "$gateway"; do docker logs --tail 70 "$container" >&2 || true; done
  fi
  docker rm -fv "$probe" "$gateway" "$bff" "$state" "$catalog" "$keycloak" "$postgres" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  docker image rm "${images[@]}" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$result"
}
trap cleanup EXIT
for service in "${services[@]}"; do
  docker build --target build -q -t "bookrush/metadata-$service-test:$suffix" -f "services/$service/Dockerfile" . >/dev/null
  docker run --rm "bookrush/metadata-$service-test:$suffix" mvn -B verify
  docker build -q -t "bookrush/metadata-$service:$suffix" -f "services/$service/Dockerfile" . >/dev/null
done
docker network create "$network" >/dev/null
docker run -d --name "$postgres" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=bookrush -e POSTGRES_USER=bookrush -e POSTGRES_PASSWORD=disposable postgres:16-alpine >/dev/null
for attempt in {1..60}; do
  if docker exec "$postgres" pg_isready -h 127.0.0.1 -U bookrush -d bookrush >/dev/null 2>&1; then break; fi
  sleep 1
done
# Upgrade a real V8 database: positive progress proves loaded content; opening
# attempts alone do not. Preserve library, history and reading progress.
docker exec "$postgres" createdb -U bookrush upgrade
for version in {1..8}; do
  for migration in services/reader-state-service/src/main/resources/db/migration/V"${version}"__*.sql; do
    docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade < "$migration" >/dev/null
  done
done
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade -c "INSERT INTO reader_state.progress(subject_key,book_id,percent) VALUES ('legacy','11111111-1111-4111-8111-111111111111',25),('legacy','22222222-2222-4222-8222-222222222222',0); INSERT INTO reader_state.recent(subject_key,book_id) VALUES ('legacy','22222222-2222-4222-8222-222222222222'); INSERT INTO reader_state.library(subject_key,book_id) VALUES ('legacy','11111111-1111-4111-8111-111111111111');" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d upgrade < services/reader-state-service/src/main/resources/db/migration/V9__seen_books.sql >/dev/null
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT count(*) FROM reader_state.seen_book WHERE subject_key='legacy' AND book_id='11111111-1111-4111-8111-111111111111'")" == 1 ]]
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT count(*) FROM reader_state.seen_book WHERE book_id='22222222-2222-4222-8222-222222222222'")" == 0 ]]
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT count(*) FROM reader_state.library WHERE subject_key='legacy'")" == 1 ]]
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT percent::integer FROM reader_state.progress WHERE subject_key='legacy' AND book_id='11111111-1111-4111-8111-111111111111'")" == 25 ]]
[[ "$(docker exec "$postgres" psql -At -U bookrush -d upgrade -c "SELECT count(*) FROM reader_state.recent WHERE subject_key='legacy'")" == 1 ]]
# Existing integration suites run against a separate disposable database.
docker exec "$postgres" createdb -U bookrush catalog_tests
docker run --rm --network "$network" -e DB_HOST=postgres -e DB_NAME=catalog_tests \
  -e DB_USERNAME=bookrush -e DB_PASSWORD=disposable "bookrush/metadata-catalog-service-test:$suffix" mvn -B -Pintegration-tests verify
cp scripts/fixtures/reader-test-realm.json "$work_dir/realm.json"
docker create --name "$keycloak" --network "$network" --network-alias keycloak \
  -e JAVA_OPTS_APPEND="-Xms128m -Xmx512m" quay.io/keycloak/keycloak:25.0 start-dev --import-realm >/dev/null
chmod 755 "$work_dir"
docker cp "$work_dir/." "$keycloak:/opt/keycloak/data/import" >/dev/null
docker start "$keycloak" >/dev/null
issuer=http://keycloak:8080/realms/search-tests
jwks="$issuer/protocol/openid-connect/certs"
docker run -d --name "$state" --network "$network" --network-alias reader-state-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=disposable \
  -e BOOKRUSH_SECURITY_ENABLED=true -e BOOKRUSH_SECURITY_ISSUER="$issuer" \
  -e BOOKRUSH_SECURITY_JWK_SET_URI="$jwks" -e BOOKRUSH_SECURITY_AUDIENCE=account \
  "bookrush/metadata-reader-state-service:$suffix" >/dev/null
docker run -d --name "$bff" --network "$network" --network-alias reader-bff-service \
  -e BOOKRUSH_SECURITY_ENABLED=true -e BOOKRUSH_SECURITY_ISSUER="$issuer" \
  -e BOOKRUSH_SECURITY_JWK_SET_URI="$jwks" -e BOOKRUSH_SECURITY_AUDIENCE=account \
  -e READER_STATE_URL=http://reader-state-service:8094 \
  -e BOOKRUSH_CORS_ALLOWED_ORIGINS=https://bookrush.jteodoro.tec.br \
  "bookrush/metadata-reader-bff-service:$suffix" >/dev/null
docker run -d --name "$catalog" --network "$network" --network-alias catalog-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=disposable \
  -e CATALOG_OIDC_ENABLED=true -e CATALOG_OIDC_ISSUER="$issuer" \
  -e CATALOG_OIDC_JWK_SET_URI="$jwks" -e CATALOG_OIDC_AUDIENCE=platform-only \
  "bookrush/metadata-catalog-service:$suffix" >/dev/null
for attempt in {1..180}; do
  if docker exec "$postgres" psql -At -U bookrush -d bookrush -c "SELECT count(*) FROM catalog.book_subject" >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush < scripts/fixtures/book-metadata.sql >/dev/null
# Simulate the stable host checkout missing a feature route. Exercise the real
# deploy override so tests cannot pass merely by copying the branch file to
# Traefik while production still reads the old host mount.
export BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG="$(cat infrastructure/traefik/dynamic.yaml)"
for variable in BACKEND_IMAGE FRONTEND_IMAGE INGESTION_IMAGE ANALYTICS_IMAGE ANALYTICS_RUNTIME_IMAGE BEHAVIOR_IMAGE RECOMMENDATION_IMAGE READER_BFF_IMAGE READER_STATE_IMAGE READER_PROFILE_IMAGE SOCIAL_IMAGE ADMIN_SERVICE_IMAGE ADMIN_WEB_IMAGE; do
  export "$variable=unused/deploy-contract:fixture"
done
cat > "$work_dir/gateway.compose.yaml" <<YAML
services:
  reverse-proxy:
    image: traefik:v3.5.3
    container_name: $gateway
    command: [--providers.docker=false, --providers.file.filename=/dynamic.yaml, --providers.file.watch=false, --entrypoints.web.address=:80]
    networks:
      default:
        aliases: [gateway, reverse-proxy]
networks:
  default:
    external: true
    name: $network
YAML
docker compose --project-name "$network" --env-file .env.example \
  -f "$work_dir/gateway.compose.yaml" -f infrastructure/jenkins/deploy-images.compose.yaml \
  create --no-build --pull never reverse-proxy >/dev/null
sed 's@ || PathPrefix(`/api/v1/reader/library/`)@@' infrastructure/traefik/dynamic.yaml > "$work_dir/stale-routes.yaml"
docker cp "$work_dir/stale-routes.yaml" "$gateway:/dynamic.yaml" >/dev/null
docker start "$gateway" >/dev/null
docker run -d --name "$probe" --network "$network" python:3.12-slim sleep 900 >/dev/null
docker exec -i "$probe" python3 - < scripts/test-book-metadata-e2e.py
[[ "$(docker exec "$gateway" cat /tmp/bookrush-traefik-dynamic.yaml)" == "$BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG" ]]
bash infrastructure/jenkins/probe-reader-library.sh "$network"
# Ensure the marker is not derived from the 50 most-recent entries.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush -c "INSERT INTO reader_state.recent(subject_key,book_id,last_opened_at) SELECT seen.subject_key,('99999999-9999-4999-8999-'||lpad(n::text,12,'0'))::uuid,clock_timestamp()+interval '1 minute' FROM reader_state.seen_book seen CROSS JOIN generate_series(1,55) n WHERE seen.book_id='11111111-1111-4111-8111-111111111111'" >/dev/null
docker exec -i "$probe" python3 - --history < scripts/test-book-metadata-e2e.py
docker restart "$state" >/dev/null
docker restart "$gateway" >/dev/null
docker exec -i "$probe" python3 - --restart < scripts/test-book-metadata-e2e.py
[[ "$(docker exec "$gateway" cat /tmp/bookrush-traefik-dynamic.yaml)" == "$BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG" ]]
docker stop "$state" >/dev/null
docker exec -i "$probe" python3 - --state-down < scripts/test-book-metadata-e2e.py
