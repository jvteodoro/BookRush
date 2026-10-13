#!/usr/bin/env bash
set -Eeuo pipefail

service="${1:-}"
image="${2:-}"
repo_root="${BOOKRUSH_REPO_ROOT:-/opt/bookrush}"
env_file="${BOOKRUSH_ENV_FILE:-/run/bookrush.env}"

case "$service" in
  admin-service|behavior-service|book-content-service|book-ingestion-service|book-analytics-service|catalog-service|publisher-service|reader-bff-service|reader-profile-service|reader-state-service|recommendation-service|social-service) ;;
  *) echo "microserviço inválido: ${service:-<vazio>}" >&2; exit 2 ;;
esac
test -n "$image"
test -f "$repo_root/compose.yaml"
test -f "$env_file"

compose=(docker compose --project-name bookrush -f "$repo_root/compose.yaml" --env-file "$env_file" --profile auth)
container="bookrush-${service}-1"
old_image="$(docker inspect --format '{{.Config.Image}}' "$container" 2>/dev/null || true)"
local_image="bookrush/${service}:local"

docker tag "$image" "$local_image"
rollback() {
  [[ -n "$old_image" ]] || return 0
  docker tag "$old_image" "$local_image"
  "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "$service" || true
}

if ! "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "$service"; then
  echo "Deploy Compose falhou para $service; executando rollback." >&2
  rollback
  exit 1
fi
echo "Deploy Compose concluído para $service usando $image."
