#!/usr/bin/env bash
set -Eeuo pipefail

image="${1:?Uso: deploy-seaweedfs.sh IMAGEM}"
repo_root="${BOOKRUSH_REPO_ROOT:-/opt/bookrush}"
env_file="${BOOKRUSH_ENV_FILE:-/run/bookrush.env}"
compose=(docker compose --project-name bookrush -f "$repo_root/compose.yaml" --env-file "$env_file" --profile auth)

[[ -f "$repo_root/compose.yaml" ]] || { echo "Compose ausente: $repo_root/compose.yaml" >&2; exit 1; }
[[ -f "$env_file" ]] || { echo "Arquivo de ambiente ausente: $env_file" >&2; exit 1; }

storage_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' bookrush-seaweedfs-1 2>/dev/null || true)"
storage_state="$(docker inspect --format '{{.State.Status}}' bookrush-seaweedfs-1 2>/dev/null || true)"
if [[ "$storage_health" != healthy && "$storage_state" != exited ]]; then
  echo 'SeaweedFS atual não está saudável; deploy interrompido para evitar substituir uma instância indisponível.' >&2
  exit 1
fi
if [[ "$storage_health" != healthy ]]; then
  echo "SeaweedFS está parado (estado=$storage_state); recuperação autorizada pela pipeline dedicada." >&2
fi

old_image="$(docker inspect --format '{{.Config.Image}}' bookrush-seaweedfs-1 2>/dev/null || true)"
rollback() {
  local status=$?
  if (( status != 0 )) && [[ -n "$old_image" ]]; then
    echo "Falha no deploy; restaurando SeaweedFS para $old_image." >&2
    SEAWEEDFS_IMAGE="$old_image" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 seaweedfs seaweedfs-oidc reverse-proxy || true
  fi
  exit "$status"
}
trap rollback EXIT

echo "Implantando SeaweedFS: $image" >&2
SEAWEEDFS_IMAGE="$image" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 seaweedfs seaweedfs-oidc reverse-proxy
new_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' bookrush-seaweedfs-1)"
[[ "$new_health" == healthy ]] || { echo "SeaweedFS não ficou saudável: $new_health" >&2; exit 1; }
echo 'SeaweedFS, proxy OIDC e reverse-proxy implantados com sucesso.' >&2
trap - EXIT
