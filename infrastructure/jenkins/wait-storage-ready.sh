#!/usr/bin/env bash
# Read-only preflight: never recreate storage or touch its persistent volume.
set -Eeuo pipefail
timeout="${1:-180}"
project="${2:-bookrush}"
[[ "$timeout" =~ ^[1-9][0-9]*$ ]] || { echo 'Timeout de storage inválido.' >&2; exit 2; }
containers="$(docker ps -aq --filter "label=com.docker.compose.project=$project" --filter label=com.docker.compose.service=seaweedfs)"
mapfile -t ids < <(printf '%s\n' "$containers" | sed '/^$/d')
if ((${#ids[@]} != 1)); then
  echo "SeaweedFS: encontrados ${#ids[@]} containers para projeto=$project serviço=seaweedfs; esperado exatamente um. Confira o daemon/projeto e a pipeline bookrush-seaweedfs." >&2
  exit 1
fi
container="${ids[0]}"
attempts=$(( (timeout + 1) / 2 ))
for ((attempt=1; attempt<=attempts; attempt++)); do
  state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container")"
  if [[ "$state" == 'running|healthy' ]]; then
    echo "SeaweedFS: $container healthy." >&2
    exit 0
  fi
  echo "SeaweedFS: $container estado=$state (verificação $attempt/$attempts)." >&2
  case "$state" in
    running\|starting|running\|unhealthy) ;;
    *) break ;;
  esac
  if ((attempt < attempts)); then sleep 2; fi
done
docker inspect --format 'storage={{.Name}} state={{.State.Status}} exit={{.State.ExitCode}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container" >&2 || true
echo 'SeaweedFS indisponível: deploy bloqueado sem alterar volumes. Confira healthcheck, espaço em disco e a pipeline bookrush-seaweedfs; não remova o volume persistente.' >&2
exit 1
