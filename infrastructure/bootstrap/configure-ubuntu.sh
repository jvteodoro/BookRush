#!/usr/bin/env bash
set -Eeuo pipefail

usage() { cat <<'EOF'
Uso: configure-ubuntu.sh [opções]
  --env-file FILE       .env privado (padrão: <clone>/.env)
  --no-up               somente validar/configurar
  --without-ci          não iniciar Jenkins
  --without-auth        não iniciar Keycloak
  --without-portal      não iniciar Backstage
  --no-build            reutilizar imagens existentes
  -h, --help            mostrar esta ajuda
EOF
}
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"; start=true; with_ci=true; with_auth=true; with_portal=true; build=true
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2;;
    --no-up) start=false; shift;;
    --without-ci) with_ci=false; shift;;
    --without-auth) with_auth=false; shift;;
    --without-portal) with_portal=false; shift;;
    --no-build) build=false; shift;;
    -h|--help) usage; exit 0;;
    *) echo "Opção desconhecida: $1" >&2; usage >&2; exit 2;;
  esac
done
[[ -f "$env_file" ]] || { echo "Arquivo de ambiente ausente: $env_file" >&2; exit 2; }
chmod 600 "$env_file" 2>/dev/null || true
command -v docker >/dev/null || { echo 'Docker ausente; execute install-ubuntu.sh.' >&2; exit 2; }
docker compose version >/dev/null || { echo 'Docker Compose v2 ausente.' >&2; exit 2; }

# O caminho não é segredo e precisa acompanhar o host para que execuções
# futuras do Jenkins, que montam o .env diretamente, usem o mesmo clone.
env_rewritten="$(mktemp "${TMPDIR:-/tmp}/bookrush-env-source.XXXXXX")"
awk -v root="$repo_dir" '
  BEGIN { found=0 }
  /^BOOKRUSH_REPO_ROOT=/ { print "BOOKRUSH_REPO_ROOT=" root; found=1; next }
  { print }
  END { if (!found) print "BOOKRUSH_REPO_ROOT=" root }
' "$env_file" > "$env_rewritten"
mv -- "$env_rewritten" "$env_file"
chmod 600 "$env_file"

runtime_env="$(mktemp "${TMPDIR:-/tmp}/bookrush-env.XXXXXX")"
trap 'rm -f -- "$runtime_env"' EXIT
cp -- "$env_file" "$runtime_env"; chmod 600 "$runtime_env"
if ! grep -Eq '^BACKSTAGE_POSTGRES_PASSWORD=.+$' "$runtime_env"; then
  postgres_line="$(grep -E '^POSTGRES_PASSWORD=.+$' "$runtime_env" | head -n1 || true)"
  [[ -n "$postgres_line" ]] || { echo 'Defina POSTGRES_PASSWORD ou BACKSTAGE_POSTGRES_PASSWORD no .env.' >&2; exit 2; }
  printf 'BACKSTAGE_POSTGRES_PASSWORD%s\n' "${postgres_line#POSTGRES_PASSWORD}" >> "$runtime_env"
fi
required_vars=(POSTGRES_PASSWORD PGADMIN_DEFAULT_EMAIL PGADMIN_DEFAULT_PASSWORD STORAGE_ACCESS_KEY STORAGE_SECRET_KEY ASSET_ADMIN_TOKEN)
[[ "$with_auth" != true ]] || required_vars+=(KEYCLOAK_ADMIN_PASSWORD)
for variable in "${required_vars[@]}"; do
  grep -Eq "^${variable}=.+$" "$runtime_env" || { echo "Variável obrigatória ausente ou vazia: $variable" >&2; exit 2; }
done
export BOOKRUSH_REPO_ROOT="$repo_dir" BOOKRUSH_ENV_FILE="$env_file" COMPOSE_PROJECT_NAME=bookrush
compose=(docker compose --project-name bookrush -f "$repo_dir/infrastructure/compose.yaml" --env-file "$runtime_env")
portal_compose=(docker compose --project-name bookrush-portal -f "$repo_dir/backstage/compose.yaml" --env-file "$runtime_env")
profiles=(); [[ "$with_auth" == true ]] && profiles+=(--profile auth); [[ "$with_ci" == true ]] && profiles+=(--profile ci)
"${compose[@]}" "${profiles[@]}" config >/dev/null
[[ "$with_portal" != true ]] || "${portal_compose[@]}" config >/dev/null

if [[ "$start" == true ]]; then
  up_args=(-d --wait --wait-timeout 300); [[ "$build" == true ]] && up_args+=(--build)
  "${compose[@]}" "${profiles[@]}" up "${up_args[@]}"
  if [[ "$with_portal" == true ]]; then
    portal_up=(-d --wait --wait-timeout 300); [[ "$build" == true ]] && portal_up+=(--build)
    "${portal_compose[@]}" up "${portal_up[@]}" backstage-postgres backstage
  fi
fi
echo "Ambiente validado no clone: $repo_dir"
[[ "$start" != true ]] || echo 'Containers iniciados e aguardados como saudáveis.'
