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
cli_image="adorsys/keycloak-config-cli:6.3.0-18.0.2"
admin_password='disposable-admin-password'
cleanup() {
  rc=$?
  if ((rc != 0)) && docker container inspect "$keycloak" >/dev/null 2>&1; then
    echo '--- Keycloak disposable logs (failure) ---' >&2
    docker logs "$keycloak" >&2 || true
  fi
  docker rm -f "$keycloak" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

mkdir -m 700 "$work_dir/rendered"
JENKINS_OIDC_CLIENT_SECRET='disposable-jenkins-secret' \
BACKSTAGE_OIDC_CLIENT_SECRET='disposable-backstage-secret' \
INGESTION_ADMIN_CLIENT_SECRET='disposable-ingestion-secret' \
INGESTION_CANONICAL_CLIENT_SECRET='disposable-canonical-secret' \
INGESTION_ASSET_CLIENT_SECRET='disposable-asset-secret' \
  python3 "$repo_dir/infrastructure/keycloak/render-config.py" \
    "$repo_dir/infrastructure/keycloak" "$work_dir/rendered"

docker network create "$network" >/dev/null
docker run -d --name "$keycloak" --network "$network" -p 127.0.0.1::8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  -v "$work_dir/rendered/bookrush-realm.json:/opt/keycloak/data/import/bookrush-realm.json:ro" \
  -v "$work_dir/rendered/bookrush-platform-realm.json:/opt/keycloak/data/import/bookrush-platform-realm.json:ro" \
  quay.io/keycloak/keycloak:25.0 start-dev --http-port=8080 --import-realm >/dev/null
port="$(docker port "$keycloak" 8080/tcp | sed -E 's/.*:([0-9]+)$/\1/')"
[[ "$port" =~ ^[0-9]+$ ]] || { echo 'Não foi possível descobrir a porta do Keycloak.' >&2; exit 1; }
for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:$port/realms/bookrush/.well-known/openid-configuration" >/dev/null 2>&1 \
      && curl -fsS "http://127.0.0.1:$port/realms/bookrush-platform/.well-known/openid-configuration" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -fsS "http://127.0.0.1:$port/realms/bookrush/.well-known/openid-configuration" >/dev/null

docker run --rm --network "$network" \
  --user "$(id -u):$(id -g)" \
  -e KEYCLOAK_URL=http://"$keycloak":8080 \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD="$admin_password" \
  -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
  -e IMPORT_FILES_LOCATIONS=/config/*.json \
  -e IMPORT_VARSUBSTITUTION_ENABLED=true \
  -v "$work_dir/rendered:/config:ro" "$cli_image"

curl -fsS "http://127.0.0.1:$port/realms/bookrush-platform/.well-known/openid-configuration" >/dev/null
echo 'Keycloak IaC render + import + reconcile: OK (ambiente descartável)'
