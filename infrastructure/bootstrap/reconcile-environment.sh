#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'EOF'
Uso: reconcile-environment.sh [--env-file FILE] [--without-auth] [--without-portal] [--without-ci]

Valida a configuração e reaplica somente os serviços declarados, preservando
volumes. O script não derruba o projeto e não executa migrations de outro owner.
EOF
}

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"; with_auth=true; with_portal=true; with_ci=true
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2;;
    --without-auth) with_auth=false; shift;;
    --without-portal) with_portal=false; shift;;
    --without-ci) with_ci=false; shift;;
    -h|--help) usage; exit 0;;
    *) echo "Opção desconhecida: $1" >&2; usage >&2; exit 2;;
  esac
done
[[ -f "$env_file" ]] || { echo "Arquivo de ambiente ausente: $env_file" >&2; exit 2; }
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker compose version >/dev/null || { echo 'Docker Compose v2 ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }

export BOOKRUSH_REPO_ROOT="$repo_dir" COMPOSE_PROJECT_NAME=bookrush COMPOSE_PARALLEL_LIMIT=1
runtime_env="$(mktemp "${TMPDIR:-/tmp}/bookrush-env-reconcile.XXXXXX")"
trap 'rm -f -- "$runtime_env"' EXIT
cp -- "$env_file" "$runtime_env"; chmod 600 "$runtime_env"
if ! grep -Eq '^BACKSTAGE_POSTGRES_PASSWORD=.+$' "$runtime_env"; then
  postgres_line="$(grep -E '^POSTGRES_PASSWORD=.+$' "$runtime_env" | head -n1 || true)"
  [[ -n "$postgres_line" ]] || { echo 'Defina POSTGRES_PASSWORD ou BACKSTAGE_POSTGRES_PASSWORD.' >&2; exit 2; }
  printf 'BACKSTAGE_POSTGRES_PASSWORD%s\n' "${postgres_line#POSTGRES_PASSWORD}" >> "$runtime_env"
fi

compose=(docker compose --project-name bookrush -f "$repo_dir/infrastructure/compose.yaml" --env-file "$runtime_env")
portal=(docker compose --project-name bookrush-portal -f "$repo_dir/backstage/compose.yaml" --env-file "$runtime_env")
profiles=(); [[ "$with_auth" == true ]] && profiles+=(--profile auth); [[ "$with_ci" == true ]] && profiles+=(--profile ci)
"${compose[@]}" "${profiles[@]}" config >/dev/null
[[ "$with_portal" != true ]] || "${portal[@]}" config >/dev/null

args=(-d --wait --wait-timeout 300)
"${compose[@]}" "${profiles[@]}" up "${args[@]}"
if [[ "$with_portal" == true ]]; then
  "${portal[@]}" up "${args[@]}" backstage-postgres backstage
fi
if [[ "$with_ci" == true ]]; then
  "${compose[@]}" ps --status running jenkins >/dev/null
fi

echo 'Reconcile concluído; healthchecks do Compose confirmaram os serviços selecionados.'
