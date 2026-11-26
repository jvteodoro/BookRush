#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${BOOKRUSH_DEMO_ENV_FILE:-}"
[[ -n "$env_file" && -r "$env_file" ]] || { echo 'Credential Secret file da demo ausente ou ilegível.' >&2; exit 2; }

# Secret file contains only synthetic/demo credentials. Do not allow Jenkins
# shell tracing to disclose values while loading it.
xtrace=false
[[ $- == *x* ]] && xtrace=true
set +x
set -a
. "$env_file"
set +a
[[ "$xtrace" == true ]] && set -x

required=(POSTGRES_PASSWORD STORAGE_ACCESS_KEY STORAGE_SECRET_KEY ASSET_ADMIN_TOKEN
  KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD INGESTION_ADMIN_CLIENT_SECRET
  CATALOG_PRODUCT_ACCESS_CLIENT_SECRET BOOK_CONTENT_PRODUCT_ACCESS_CLIENT_SECRET
  READER_STATE_PRODUCT_ACCESS_CLIENT_SECRET READER_PROFILE_PRODUCT_ACCESS_CLIENT_SECRET
  SOCIAL_PRODUCT_ACCESS_CLIENT_SECRET BEHAVIOR_PRODUCT_ACCESS_CLIENT_SECRET BEHAVIOR_OPERATIONS_CLIENT_SECRET
  READER_BFF_SESSION_CLIENT_SECRET READER_BFF_SUPERVISION_CLIENT_SECRET
  SUBSCRIPTION_FAMILY_CLIENT_SECRET SUBSCRIPTION_BILLING_CLIENT_SECRET FAMILY_IDENTITY_CLIENT_SECRET FAMILY_SUPERVISION_ADMIN_SECRET
  DEMO_ADULT_USERNAME DEMO_ADULT_PASSWORD DEMO_MINOR_USERNAME DEMO_MINOR_PASSWORD
  FAMILY_MEMBERSHIP_CLIENT_SECRET)
for key in "${required[@]}"; do
  [[ -n "${!key:-}" && "${!key}" != *change-this* ]] || {
    echo "Credential Secret file da demo incompleta: $key ausente ou placeholder." >&2
    exit 2
  }
done
export KEYCLOAK_ADMIN="${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-admin}"
export KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD"

project=bookrush-demo
network=bookrush-demo_bookrush
export BOOKRUSH_COMPOSE_NAME="$project" BOOKRUSH_NETWORK_NAME="$network"
export BOOKRUSH_REPO_ROOT="${BOOKRUSH_REPO_ROOT:-/opt/bookrush}"
export BOOKRUSH_ENVIRONMENT=development
export BOOKRUSH_PUBLIC_URL=http://localhost:19080
export KEYCLOAK_PUBLIC_URL=http://localhost:19088
export VITE_OIDC_AUTHORITY=http://localhost:19088/realms/bookrush
export VITE_OIDC_CLIENT_ID=bookrush-web

# Keep every browser token issuer bound to this isolated Keycloak. Internal
# JWKS/token calls stay on Compose DNS; no product realm from production is used.
export BOOKRUSH_PRODUCT_SECURITY_ISSUER=http://localhost:19088/realms/bookrush
export BOOKRUSH_ADMIN_SECURITY_ISSUER=http://localhost:19088/realms/bookrush-platform
export BOOKRUSH_SECURITY_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs
export BOOKRUSH_PRODUCT_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs
export CATALOG_OIDC_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export CATALOG_PRODUCT_OIDC_ISSUER="$BOOKRUSH_PRODUCT_SECURITY_ISSUER"
export INGESTION_OIDC_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export BOOKRUSH_S2S_SECURITY_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export ANALYTICS_OIDC_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token
export KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush
export INGESTION_OIDC_TOKEN_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token
export SEAWEEDFS_OIDC_ISSUER_URL="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export SEAWEEDFS_OIDC_LOGIN_URL=http://localhost:19088/realms/bookrush-platform/protocol/openid-connect/auth
export SEAWEEDFS_OIDC_REDIRECT_URL=http://localhost:19081/oauth2/callback
export AUTH_OIDC_METADATA_URL=http://keycloak:8080/realms/bookrush-platform/.well-known/openid-configuration

# All published ports remain loopback-only. A browser uses localhost directly
# or a pair of SSH forwards; there is no public DNS or host Nginx route.
export BOOKRUSH_PROXY_PORT=19081 BOOKRUSH_FRONTEND_PORT=19080
export BOOKRUSH_KEYCLOAK_PORT=19088 BOOKRUSH_POSTGRES_PORT=19432
export BOOKRUSH_S3_PORT=19333 BOOKRUSH_PGADMIN_PORT=19083
export BOOKRUSH_INGESTION_PORT=19090 BOOKRUSH_ANALYTICS_PORT=19091
export BOOKRUSH_ADMIN_WEB_PORT=19084

# The educational demo is local/tunnel-only and uses synthetic identities.
# These flags cannot affect production because the project, Keycloak and DB are
# isolated; production checkout still requires DEPLOY_ENVIRONMENT=production.
export KEYCLOAK_AGE_PROFILE_ENABLED=true KEYCLOAK_READER_STATE_CLIENT_ENABLED=true
export KEYCLOAK_READING_SUPERVISION_ENABLED=true KEYCLOAK_SESSION_BOOTSTRAP_ENABLED=true
export KEYCLOAK_CONTENT_DISTRIBUTION_CLIENTS_ENABLED=true KEYCLOAK_COMMUNITY_CLIENTS_ENABLED=true
export READER_STATE_SUPERVISION_ENABLED=true READER_BFF_SESSION_ENABLED=true
export READER_BFF_SUPERVISION_ENABLED=true READER_BFF_SUPERVISED_COMMENTS_ENABLED=true
export SOCIAL_SUPERVISED_COMMENTS_ENABLED=true COMMUNITY_PRODUCT_ACCESS_ENABLED=true
export BOOKRUSH_PROTECTED_DISTRIBUTION=true BOOKRUSH_STORAGE_EDGE_CLOSED=true
export SUBSCRIPTION_MOCK_ENABLED=true SUBSCRIPTION_SPRING_PROFILES=billing-mock
export FAMILY_SUPERVISION_SIMULATION=true FAMILY_SPRING_PROFILES=supervision-mock
export FAMILY_PRODUCT_ACCESS_AGE_VERIFICATION_REQUIRED=false
export READER_STATE_PRODUCT_ACCESS_ENABLED=true
export BOOKRUSH_DEMO_PUBLIC_REGISTRATION=false
export CATALOG_CORS_ALLOWED_ORIGINS=http://localhost:19080
export BOOKRUSH_CORS_ALLOWED_ORIGINS=http://localhost:19080
export FAMILY_CORS_ORIGINS=http://localhost:19080
export SUBSCRIPTION_CORS_ORIGINS=http://localhost:19080
export COMMUNITY_PRODUCT_HUMAN_CLIENTS=bookrush-web,bookrush-mobile
export STORAGE_ALLOWED_ORIGINS=http://localhost:19080

export CONTENT_PRODUCT_ACCESS_ENABLED=true
export CONTENT_PRODUCT_ACCESS_ORIGIN=http://family-service:8103
export CONTENT_PRODUCT_ACCESS_TOKEN_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token
export CONTENT_PRODUCT_ACCESS_JWKS_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs
export CONTENT_PRODUCT_ACCESS_WORKLOAD_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export CONTENT_PRODUCT_ACCESS_ALLOW_INSECURE_HTTP=true
export READER_STATE_PRODUCT_ACCESS_ORIGIN=http://family-service:8103
export READER_STATE_PRODUCT_ACCESS_TOKEN_URI="$CONTENT_PRODUCT_ACCESS_TOKEN_URI"
export READER_STATE_PRODUCT_ACCESS_JWKS_URI="$CONTENT_PRODUCT_ACCESS_JWKS_URI"
export READER_STATE_PRODUCT_ACCESS_WORKLOAD_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export READER_STATE_PRODUCT_ACCESS_ALLOW_INSECURE_HTTP=true
export COMMUNITY_PRODUCT_ACCESS_ORIGIN=http://family-service:8103
export COMMUNITY_PRODUCT_ACCESS_TOKEN_URI="$CONTENT_PRODUCT_ACCESS_TOKEN_URI"
export COMMUNITY_PRODUCT_ACCESS_JWKS_URI="$CONTENT_PRODUCT_ACCESS_JWKS_URI"
export COMMUNITY_PRODUCT_ACCESS_WORKLOAD_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export COMMUNITY_PRODUCT_ACCESS_ALLOW_INSECURE_HTTP=true
export READER_BFF_SESSION_FAMILY_URL=http://family-service:8103
export READER_BFF_SESSION_TOKEN_URL="$CONTENT_PRODUCT_ACCESS_TOKEN_URI"
export READER_BFF_SESSION_JWKS="$CONTENT_PRODUCT_ACCESS_JWKS_URI"
export READER_BFF_SESSION_WORKLOAD_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export READER_BFF_SESSION_ALLOW_INSECURE_HTTP=true
export READER_BFF_SUPERVISION_TOKEN_URI="$CONTENT_PRODUCT_ACCESS_TOKEN_URI"
export READER_BFF_SUPERVISION_ALLOW_INSECURE_HTTP=true
export READER_BFF_SUPERVISION_SOCIAL_URL=http://social-service:8096
export READER_BFF_READER_STATE_URL=http://reader-state-service:8094
export FAMILY_TOKEN_URL="$CONTENT_PRODUCT_ACCESS_TOKEN_URI"
export FAMILY_PRODUCT_ISSUER="$BOOKRUSH_PRODUCT_SECURITY_ISSUER"
export FAMILY_WORKLOAD_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export FAMILY_PRODUCT_JWKS=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs
export FAMILY_WORKLOAD_JWKS="$CONTENT_PRODUCT_ACCESS_JWKS_URI"
export FAMILY_PRODUCT_ACCESS_WORKLOADS='bookrush-reader-state-s2s=PRODUCT_USE:READER_STATE,SUPERVISE_HISTORY:READING_HISTORY;bookrush-catalog-product-access-s2s=PRODUCT_USE:CATALOG,PRODUCT_USE:BOOK,PRODUCT_USE:ASSET;bookrush-book-content-product-access-s2s=PRODUCT_USE:BOOK,PRODUCT_USE:ASSET;bookrush-social-product-access-s2s=PRODUCT_USE:COMMENT,PRODUCT_USE:BOOK;bookrush-reader-profile-product-access-s2s=PRODUCT_USE:PROFILE;bookrush-behavior-product-access-s2s=PRODUCT_USE:TELEMETRY;bookrush-reader-bff-supervision-s2s=SUPERVISE_HISTORY:READING_HISTORY,SUPERVISE_COMMENTS:COMMENT'
export SUBSCRIPTION_PRODUCT_ISSUER="$BOOKRUSH_PRODUCT_SECURITY_ISSUER"
export SUBSCRIPTION_WORKLOAD_ISSUER="$BOOKRUSH_ADMIN_SECURITY_ISSUER"
export SUBSCRIPTION_PRODUCT_JWKS="$FAMILY_PRODUCT_JWKS"
export SUBSCRIPTION_WORKLOAD_JWKS="$FAMILY_WORKLOAD_JWKS"
export BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG="$(BOOKRUSH_PROTECTED_DISTRIBUTION=true BOOKRUSH_STORAGE_EDGE_CLOSED=true \
  python3 "$repo_dir/scripts/render-traefik-config.py" "$repo_dir/infrastructure/traefik/dynamic.yaml")"

images=(BACKEND_IMAGE FRONTEND_IMAGE INGESTION_IMAGE ANALYTICS_IMAGE ANALYTICS_RUNTIME_IMAGE
  BEHAVIOR_IMAGE RECOMMENDATION_IMAGE READER_BFF_IMAGE READER_STATE_IMAGE READER_PROFILE_IMAGE
  SOCIAL_IMAGE ADMIN_SERVICE_IMAGE ADMIN_WEB_IMAGE PUBLISHER_IMAGE BOOK_CONTENT_IMAGE
  FAMILY_IMAGE SUBSCRIPTION_IMAGE SEAWEEDFS_IMAGE KEYCLOAK_IMAGE)
for variable in "${images[@]}"; do
  ref="${!variable:-}"
  [[ -n "$ref" && "$ref" == *":sha-${GIT_COMMIT:-}" ]] || {
    echo "A imagem $variable não está versionada pelo SHA completo desta revisão." >&2
    exit 2
  }
  docker image inspect "$ref" >/dev/null 2>&1 || {
    echo "Imagem obrigatória ausente no daemon do deploy: $variable." >&2
    exit 2
  }
done

compose=(docker compose --project-name "$project"
  -f "$repo_dir/infrastructure/compose.yaml"
  -f "$repo_dir/infrastructure/compose.subscription.yaml"
  -f "$repo_dir/infrastructure/compose.family.yaml"
  -f "$repo_dir/infrastructure/jenkins/deploy-images.compose.yaml"
  --env-file "$env_file")
"${compose[@]}" config --quiet

infra_services=(postgres redis seaweedfs keycloak)
infra_image_services=(keycloak seaweedfs)
app_services=(catalog-service book-ingestion-service book-content-service book-analytics-runtime
  book-analytics-service behavior-service recommendation-service reader-bff-service
  reader-state-service reader-profile-service social-service admin-service admin-web
  frontend publisher-service subscription-service family-service reverse-proxy)
services=("${infra_services[@]}" "${app_services[@]}")
old_routes="$(docker compose --project-name "$project" -f "$repo_dir/infrastructure/compose.yaml" \
  -f "$repo_dir/infrastructure/compose.subscription.yaml" -f "$repo_dir/infrastructure/compose.family.yaml" \
  -f "$repo_dir/infrastructure/jenkins/deploy-images.compose.yaml" --env-file "$env_file" \
  exec -T reverse-proxy sh -c 'cat /tmp/bookrush-traefik-dynamic.yaml 2>/dev/null || true' 2>/dev/null || true)"
declare -A old_images=()
for service in "${infra_image_services[@]}"; do
  container="$("${compose[@]}" ps -q "$service" 2>/dev/null || true)"
  old_images[$service]=""
  [[ -n "$container" ]] && old_images[$service]="$(docker inspect --format '{{.Config.Image}}' "$container" 2>/dev/null || true)"
done
for service in "${app_services[@]}"; do
  container="$("${compose[@]}" ps -q "$service" 2>/dev/null || true)"
  old_images[$service]=""
  [[ -n "$container" ]] && old_images[$service]="$(docker inspect --format '{{.Config.Image}}' "$container" 2>/dev/null || true)"
done

mkdir -p "$repo_dir/artifacts"
release_file="$repo_dir/artifacts/premium-family-demo-release.json"
backup_volume="${project}_backups"
docker volume create "$backup_volume" >/dev/null
db_container="$("${compose[@]}" ps -q postgres 2>/dev/null || true)"
if [[ -n "$db_container" ]]; then
  backup_name="pre-${GIT_COMMIT:-unknown}-$(date -u +%Y%m%dT%H%M%SZ).dump"
  docker run --rm --network "$network" -e PGPASSWORD="$POSTGRES_PASSWORD" postgres:16-alpine \
    pg_dump -h postgres -U "${POSTGRES_USER:-bookrush}" -d "${POSTGRES_DB:-bookrush}" --format=custom --no-owner \
    | docker run --rm -i -v "$backup_volume:/backup" alpine:3.20 sh -ec "umask 077; cat > '/backup/$backup_name'"
fi

record_release() {
  python3 - "$release_file" "$GIT_COMMIT" "$project" "${images[*]}" <<'PY'
import json, os, subprocess, sys
path, sha, project, variables = sys.argv[1:]
image_refs = {}
for name in variables.split():
    ref = os.environ.get(name, "")
    if ref:
        image_refs[name] = {"ref": ref, "digest": subprocess.run(
            ["docker", "image", "inspect", "--format", "{{.Id}}", ref],
            check=True, capture_output=True, text=True).stdout.strip()}
with open(path, "w", encoding="utf-8") as output:
    json.dump({"commit": sha, "project": project, "images": image_refs}, output, indent=2)
    output.write("\n")
PY
}

rollback_state=none
rollback() {
  [[ "$rollback_state" != running ]] && return 1
  local all_old=true
  for service in "${infra_image_services[@]}"; do [[ -n "${old_images[$service]}" ]] || all_old=false; done
  for service in "${app_services[@]}"; do [[ -n "${old_images[$service]}" ]] || all_old=false; done
  if ! $all_old; then
    echo 'Rollback binário não é seguro: release anterior incompleta ou schema já expandido. Mantendo o demo isolado em modo falho para recuperação.' >&2
    return 1
  fi
  export KEYCLOAK_IMAGE="${old_images[keycloak]}"
  export SEAWEEDFS_IMAGE="${old_images[seaweedfs]}"
  "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${infra_image_services[@]}"
  export BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG="$old_routes"
  for service in "${app_services[@]}"; do
    case "$service" in
      catalog-service) BACKEND_IMAGE="${old_images[$service]}";;
      frontend) FRONTEND_IMAGE="${old_images[$service]}";;
      book-ingestion-service) INGESTION_IMAGE="${old_images[$service]}";;
      book-analytics-service) ANALYTICS_IMAGE="${old_images[$service]}";;
      book-analytics-runtime) ANALYTICS_RUNTIME_IMAGE="${old_images[$service]}";;
      behavior-service) BEHAVIOR_IMAGE="${old_images[$service]}";;
      recommendation-service) RECOMMENDATION_IMAGE="${old_images[$service]}";;
      reader-bff-service) READER_BFF_IMAGE="${old_images[$service]}";;
      reader-state-service) READER_STATE_IMAGE="${old_images[$service]}";;
      reader-profile-service) READER_PROFILE_IMAGE="${old_images[$service]}";;
      social-service) SOCIAL_IMAGE="${old_images[$service]}";;
      admin-service) ADMIN_SERVICE_IMAGE="${old_images[$service]}";;
      admin-web) ADMIN_WEB_IMAGE="${old_images[$service]}";;
      publisher-service) PUBLISHER_IMAGE="${old_images[$service]}";;
      book-content-service) BOOK_CONTENT_IMAGE="${old_images[$service]}";;
      family-service) FAMILY_IMAGE="${old_images[$service]}";;
      subscription-service) SUBSCRIPTION_IMAGE="${old_images[$service]}";;
    esac
  done
  "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${app_services[@]}"
  rollback_state=restored
}
on_failure() {
  status=$?
  (( status == 0 )) && return
  trap - ERR
  echo 'Deploy demo falhou; coletando estado e tentando rollback compatível.' >&2
  for service in "${services[@]}"; do
    cid="$("${compose[@]}" ps -q "$service" 2>/dev/null || true)"
    [[ -n "$cid" ]] || continue
    docker inspect --format 'service={{.Name}} status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} image={{.Config.Image}}' "$cid" >&2 || true
    docker logs --tail=80 "$cid" 2>&1 | sed -E 's/(Authorization: Bearer )[[:graph:]]+/\1[redacted]/g; s/(password|secret|token)=([^[:space:]]+)/\1=[redacted]/Ig' >&2 || true
  done
  rollback || echo 'Rollback automático indisponível ou falhou; containers e volumes foram preservados para recuperação.' >&2
  exit "$status"
}
trap on_failure ERR

echo 'Validando imagens completas e configuração Compose isolada da demo.' >&2
rollback_state=running
"${compose[@]}" up -d --no-build --wait --wait-timeout 240 "${infra_services[@]}"
for attempt in {1..30}; do
  if docker run --rm --network "$network" curlimages/curl:8.10.1 -fsS \
    http://keycloak:8080/realms/master/.well-known/openid-configuration >/dev/null; then break; fi
  (( attempt < 30 )) || { echo 'Keycloak da demo não ficou disponível.' >&2; false; }
  sleep 5
done

export HOSTNAME="${HOSTNAME:-$(hostname)}"
export BUILD_NUMBER="${BUILD_NUMBER:-manual}"
export INGESTION_ADMIN_CLIENT_SECRET="${INGESTION_ADMIN_CLIENT_SECRET}"
bash "$repo_dir/scripts/reconcile-keycloak-agent.sh"
docker run --rm -i --volumes-from "$HOSTNAME" -w "$repo_dir" --network "$network" \
  -e KEYCLOAK_ADMIN_URL=http://keycloak:8080 -e KEYCLOAK_CONFIG_CLI_ADMIN_USER \
  -e KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD python:3.12-slim \
  python3 - < "$repo_dir/infrastructure/keycloak/configure-demo-realm.py"
docker run --rm -i --volumes-from "$HOSTNAME" -w "$repo_dir" --network "$network" \
  -e KEYCLOAK_ADMIN_URL=http://keycloak:8080 -e KEYCLOAK_CONFIG_CLI_ADMIN_USER \
  -e KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD -e DEMO_ADULT_USERNAME -e DEMO_ADULT_PASSWORD \
  -e DEMO_MINOR_USERNAME -e DEMO_MINOR_PASSWORD python:3.12-slim \
  python3 - < "$repo_dir/infrastructure/keycloak/seed-demo-users.py"
if [[ "${KEYCLOAK_AGE_PROFILE_ENABLED:-false}" == true ]]; then
  KEYCLOAK_AGE_ADMIN_URL=http://keycloak:8080 \
  KEYCLOAK_AGE_PRODUCT_ISSUER="$BOOKRUSH_PRODUCT_SECURITY_ISSUER" \
  KEYCLOAK_CONFIG_CLI_ADMIN_USER="$KEYCLOAK_CONFIG_CLI_ADMIN_USER" \
  KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD="$KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" \
    docker run --rm --volumes-from "$HOSTNAME" --network "$network" -w "$repo_dir" \
      -e KEYCLOAK_AGE_ADMIN_URL -e KEYCLOAK_AGE_PRODUCT_ISSUER \
      -e KEYCLOAK_CONFIG_CLI_ADMIN_USER -e KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD \
      python:3.12-slim python3 infrastructure/keycloak/reconcile-age-profile.py
fi

"${compose[@]}" up -d --no-build --force-recreate --wait --wait-timeout 300 "${app_services[@]}"
for service in reverse-proxy family-service subscription-service reader-bff-service reader-state-service book-content-service; do
  cid="$("${compose[@]}" ps -q "$service")"
  [[ -n "$cid" ]] && docker inspect --format '{{.State.Health.Status}}' "$cid" | grep -qx healthy
done
for path in /api/v1/session /api/v1/family/me /api/v1/subscription/me /api/v1/reader/supervision/demo/history; do
  status="$(curl --silent --show-error --max-time 8 -o /dev/null -w '%{http_code}' "http://127.0.0.1:19081$path" || true)"
  case "$status" in 401|403|404) ;; *) echo "Gateway demo: rota $path retornou HTTP ${status:-000}, esperado negar sessão anônima." >&2; false;; esac
done
curl --fail --silent --show-error --max-time 8 http://127.0.0.1:19081/ >/dev/null
record_release
trap - ERR
echo "Demo Compose implantada localmente: http://127.0.0.1:19081 (commit ${GIT_COMMIT:-unknown})."
echo 'O gateway e o Keycloak ficam em loopback; use túnel SSH para uma máquina de demonstração remota.'
