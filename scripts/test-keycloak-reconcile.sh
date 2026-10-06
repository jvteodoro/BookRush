#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable proof that the checked-in realms can be rendered with runtime
# secrets and reconciled by keycloak-config-cli. It never uses the project
# Compose network, volumes, or .env file.
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-keycloak-reconcile.XXXXXX")"
network="bookrush-keycloak-reconcile-$RANDOM"
keycloak="bookrush-keycloak-reconcile-$RANDOM"
renderer="bookrush-keycloak-render-$RANDOM"
cli="bookrush-keycloak-cli-$RANDOM"
cli_image="adorsys/keycloak-config-cli:6.3.0-18.0.2"
admin_password='disposable-admin-password'
cleanup() {
  rc=$?
  if ((rc != 0)) && docker container inspect "$keycloak" >/dev/null 2>&1; then
    echo '--- Keycloak disposable logs (failure) ---' >&2
    docker logs "$keycloak" >&2 || true
  fi
  docker rm -f "$cli" >/dev/null 2>&1 || true
  docker rm -f "$renderer" >/dev/null 2>&1 || true
  docker rm -f "$keycloak" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

mkdir -m 700 "$work_dir/rendered"
docker create --name "$renderer" \
  -e JENKINS_OIDC_CLIENT_SECRET='disposable-jenkins-secret' \
  -e BACKSTAGE_OIDC_CLIENT_SECRET='disposable-backstage-secret' \
  -e INGESTION_ADMIN_CLIENT_SECRET='disposable-ingestion-secret' \
  -e INGESTION_CANONICAL_CLIENT_SECRET='disposable-canonical-secret' \
  -e INGESTION_ASSET_CLIENT_SECRET='disposable-asset-secret' \
  --entrypoint python python:3.12-alpine \
  /tmp/render-config.py /tmp/source /tmp/rendered >/dev/null
docker cp "$repo_dir/infrastructure/keycloak/." "$renderer:/tmp/source"
docker cp "$repo_dir/infrastructure/keycloak/render-config.py" "$renderer:/tmp/render-config.py"
docker start -a "$renderer" >/dev/null
docker cp "$renderer:/tmp/rendered/." "$work_dir/rendered"

docker network create "$network" >/dev/null
docker create --name "$keycloak" --network "$network" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /opt/keycloak/bookrush-*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm' >/dev/null
docker cp "$work_dir/rendered/bookrush-realm.json" "$keycloak:/opt/keycloak/bookrush-realm.json"
docker cp "$work_dir/rendered/bookrush-platform-realm.json" "$keycloak:/opt/keycloak/bookrush-platform-realm.json"
docker start "$keycloak" >/dev/null
probe="$(docker run -d --network "container:$keycloak" --entrypoint /bin/sh \
  curlimages/curl:8.10.1 -c 'sleep 180')"
cleanup_probe() { docker rm -f "$probe" >/dev/null 2>&1 || true; }
trap 'cleanup_probe; cleanup' EXIT
for _ in $(seq 1 90); do
  if docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration' >/dev/null 2>&1 \
      && docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush-platform/.well-known/openid-configuration' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration' >/dev/null

docker create --name "$cli" --network "$network" \
  --user "$(id -u):$(id -g)" \
  -e KEYCLOAK_URL=http://"$keycloak":8080 \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD="$admin_password" \
  -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
  -e IMPORT_FILES_LOCATIONS=/tmp/config/*.json \
  -e IMPORT_VARSUBSTITUTION_ENABLED=true \
  --entrypoint /bin/sh "$cli_image" \
  -c 'mkdir -p /tmp/config && cp /tmp/bookrush-*.json /tmp/config/ && exec java $JAVA_OPTS -jar /app/keycloak-config-cli.jar' >/dev/null
docker cp "$work_dir/rendered/bookrush-realm.json" "$cli:/tmp/bookrush-realm.json"
docker cp "$work_dir/rendered/bookrush-platform-realm.json" "$cli:/tmp/bookrush-platform-realm.json"
docker start -a "$cli"

docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush-platform/.well-known/openid-configuration' >/dev/null
echo 'Keycloak IaC render + import + reconcile: OK (ambiente descartável)'
