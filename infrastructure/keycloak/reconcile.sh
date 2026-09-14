#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2;;
    -h|--help) echo 'Uso: infrastructure/keycloak/reconcile.sh [--env-file FILE]'; exit 0;;
    *) echo "Opção desconhecida: $1" >&2; exit 2;;
  esac
done
[[ -f "$env_file" ]] || { echo "Arquivo ausente: $env_file" >&2; exit 2; }
set -a
# Load only the protected runtime file into the child environment; never print
# it or pass the expanded values to `docker compose config`.
. "$env_file"
set +a
grep -Eq '^KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD=.+$' "$env_file" || {
  echo 'KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD ausente; não iniciar reconcile parcial.' >&2; exit 2;
}
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }
export BOOKRUSH_REPO_ROOT="$repo_dir"
export KEYCLOAK_CONFIG_CLI_USER="$(id -u):$(id -g)"
config_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-keycloak-config.XXXXXX")"
chmod 700 "$config_dir"
cleanup() { rm -rf -- "$config_dir"; }
trap cleanup EXIT
python3 "$repo_dir/infrastructure/keycloak/render-config.py" \
  "$repo_dir/infrastructure/keycloak" "$config_dir"
export KEYCLOAK_CONFIG_DIR="$config_dir"
docker compose --project-name bookrush --env-file "$env_file" \
  -f "$repo_dir/infrastructure/compose.yaml" --profile auth --profile iam-reconcile \
  run --rm keycloak-config-cli
