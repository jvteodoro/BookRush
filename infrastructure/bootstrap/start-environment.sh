#!/usr/bin/env bash
set -Eeuo pipefail

usage() { cat <<'EOF'
Uso: start-environment.sh [opções]
  --env-file FILE       .env privado (padrão: <clone>/.env)
  --no-build            reutilizar imagens existentes
  --without-ci          não iniciar Jenkins
  --without-auth        não iniciar Keycloak
  --without-portal      não iniciar Backstage
  -h, --help            mostrar esta ajuda
EOF
}
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"; build=true; with_ci=true; with_auth=true; with_portal=true
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2;;
    --no-build) build=false; shift;;
    --without-ci) with_ci=false; shift;;
    --without-auth) with_auth=false; shift;;
    --without-portal) with_portal=false; shift;;
    -h|--help) usage; exit 0;;
    *) echo "Opção desconhecida: $1" >&2; usage >&2; exit 2;;
  esac
done
[[ -f "$env_file" ]] || { echo "Arquivo de ambiente ausente: $env_file" >&2; exit 2; }
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker compose version >/dev/null || { echo 'Docker Compose v2 ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker; abra uma nova sessão após entrar no grupo docker ou execute com sudo.' >&2; exit 2; }
if [[ "$build" == true ]] && ! docker buildx version >/dev/null 2>&1; then
  echo 'Docker Buildx ausente. Execute install-ubuntu.sh ou instale docker-buildx-plugin antes de usar --build.' >&2
  exit 2
fi
runtime_env="$(mktemp "${TMPDIR:-/tmp}/bookrush-env-start.XXXXXX")"
trap 'rm -f -- "$runtime_env"' EXIT
cp -- "$env_file" "$runtime_env"; chmod 600 "$runtime_env"
if ! grep -Eq '^BACKSTAGE_POSTGRES_PASSWORD=.+$' "$runtime_env"; then
  postgres_line="$(grep -E '^POSTGRES_PASSWORD=.+$' "$runtime_env" | head -n1 || true)"
  [[ -n "$postgres_line" ]] || { echo 'Defina POSTGRES_PASSWORD ou BACKSTAGE_POSTGRES_PASSWORD no .env.' >&2; exit 2; }
  printf 'BACKSTAGE_POSTGRES_PASSWORD%s\n' "${postgres_line#POSTGRES_PASSWORD}" >> "$runtime_env"
fi
export BOOKRUSH_REPO_ROOT="$repo_dir" BOOKRUSH_ENV_FILE="$env_file" COMPOSE_PROJECT_NAME=bookrush
export DOCKER_GID="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo 999)"
export COMPOSE_PARALLEL_LIMIT=1
compose=(docker compose --project-name bookrush -f "$repo_dir/infrastructure/compose.yaml" --env-file "$runtime_env")
portal=(docker compose --project-name bookrush-portal -f "$repo_dir/backstage/compose.yaml" --env-file "$runtime_env")
profiles=(); [[ "$with_auth" == true ]] && profiles+=(--profile auth)
"${compose[@]}" "${profiles[@]}" config >/dev/null
[[ "$with_portal" != true ]] || "${portal[@]}" config >/dev/null
up_args=(-d --wait --wait-timeout 300); [[ "$build" == true ]] && up_args+=(--build)
"${compose[@]}" "${profiles[@]}" up "${up_args[@]}"
if [[ "$with_portal" == true ]]; then
  portal_args=(-d --wait --wait-timeout 300); [[ "$build" == true ]] && portal_args+=(--build)
  "${portal[@]}" up "${portal_args[@]}" backstage-postgres backstage
fi
if [[ "$with_ci" == true ]]; then
  jenkins_args=(--env-file "$env_file"); [[ "$build" == true ]] || jenkins_args+=(--no-build)
  bash "$repo_dir/infrastructure/bootstrap/start-jenkins.sh" "${jenkins_args[@]}"
fi
echo 'Ambiente BookRush iniciado e aguardado conforme os healthchecks.'
