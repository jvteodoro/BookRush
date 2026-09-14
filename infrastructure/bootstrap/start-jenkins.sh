#!/usr/bin/env bash
set -Eeuo pipefail

usage() { cat <<'EOF'
Uso: start-jenkins.sh [--env-file FILE] [--no-build]
Inicia somente o Jenkins do stack BookRush, preservando jenkins_home.
EOF
}
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"; build=true
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2;;
    --no-build) build=false; shift;;
    -h|--help) usage; exit 0;;
    *) echo "Opção desconhecida: $1" >&2; usage >&2; exit 2;;
  esac
done
[[ -f "$env_file" ]] || { echo "Arquivo de ambiente ausente: $env_file" >&2; exit 2; }
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker compose version >/dev/null || { echo 'Docker Compose v2 ausente.' >&2; exit 2; }
[[ -S /var/run/docker.sock ]] || { echo 'Socket Docker ausente: /var/run/docker.sock' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker; abra uma nova sessão após entrar no grupo docker ou execute com sudo.' >&2; exit 2; }
if [[ "$build" == true ]] && ! docker buildx version >/dev/null 2>&1; then
  echo 'Docker Buildx ausente. Execute install-ubuntu.sh ou instale docker-buildx-plugin antes de usar --build.' >&2
  exit 2
fi
export BOOKRUSH_REPO_ROOT="$repo_dir" BOOKRUSH_ENV_FILE="$env_file"
export DOCKER_GID="$(stat -c '%g' /var/run/docker.sock)"
export COMPOSE_PARALLEL_LIMIT=1
compose=(docker compose --project-name bookrush -f "$repo_dir/infrastructure/compose.yaml" --env-file "$env_file" --profile ci)
"${compose[@]}" config >/dev/null
# Remove only the previous Jenkins container. The named jenkins_home volume is
# intentionally preserved. This also handles containers left by a deployment
# that used the same project labels with an older image.
if ! "${compose[@]}" rm --force --stop jenkins; then
  mapfile -t stale_ids < <(docker ps -aq --filter 'label=com.docker.compose.project=bookrush' --filter 'label=com.docker.compose.service=jenkins')
  if ((${#stale_ids[@]})); then
    docker rm -f "${stale_ids[@]}"
  fi
fi
args=(-d --wait --wait-timeout 300); [[ "$build" == true ]] && args+=(--build)
"${compose[@]}" up "${args[@]}" jenkins
"${compose[@]}" ps jenkins
echo 'Jenkins iniciado e saudável.'
