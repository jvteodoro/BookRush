#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${OBSERVABILITY_ENV_FILE:-/run/bookrush-observability.env}"
compose_file="$repo_dir/infrastructure/observability/compose.yaml"
config_volume="${OBS_CONFIG_VOLUME:-bookrush-observability-config}"
project="${OBSERVABILITY_COMPOSE_NAME:-bookrush-observability}"

[[ -r "$env_file" ]] || { echo 'Ambiente protegido de observabilidade ausente ou ilegível.' >&2; exit 2; }
command -v docker >/dev/null || { echo 'Docker CLI ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Daemon Docker indisponível.' >&2; exit 2; }
docker network inspect "${BOOKRUSH_NETWORK_NAME:-bookrush_bookrush}" >/dev/null 2>&1 || {
  echo 'Rede privada BookRush ausente; implante primeiro a aplicação.' >&2
  exit 2
}

compose=(docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file")
"${compose[@]}" config --quiet

# Jenkins workspaces may live only in a Docker volume. Stream the versioned
# configuration into a named volume so the host daemon never bind-mounts that
# private workspace path.
tar -C "$repo_dir/infrastructure/observability" -czf - \
  alloy grafana loki prometheus tempo |
  docker run --rm -i --network none -v "$config_volume:/config" alpine:3.20 \
    sh -ec 'tar -xzf - -C /config'

OBS_CONFIG_VOLUME="$config_volume" "${compose[@]}" up -d --wait
"${compose[@]}" ps
