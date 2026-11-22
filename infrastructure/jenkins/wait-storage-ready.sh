#!/usr/bin/env bash
# Never recreate storage or its volume. Deploy may opt into one start attempt.
set -Eeuo pipefail
set +x
filter_diagnostics() {
  head -c 32768 | sed -E \
    '/password|passwd|secret|token|authorization|credential|signature|access.?key|bearer|:\/\/[^ /]+@/I c\[linha omitida: possível credencial]'
}
diagnostic() {
  # Bound daemon calls and output. Never print Config.Env, healthcheck commands
  # or full inspect payloads; storage logs may contain credentials.
  local title="$1"
  shift
  echo "--- SeaweedFS: $title ---" >&2
  if ! command -v timeout >/dev/null 2>&1; then
    echo 'Diagnóstico omitido: comando timeout indisponível no agente.' >&2
    return 0
  fi
  { timeout 15 "$@" 2>&1 || true; } | filter_diagnostics >&2 || true
}
failure_diagnostics() {
  local container="$1"
  diagnostic 'estado e motivo de término' docker inspect --format \
    'storage={{.Name}} state={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}} started={{.State.StartedAt}} finished={{.State.FinishedAt}} restartCount={{.RestartCount}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container"
  diagnostic 'últimos resultados do healthcheck' docker inspect --format \
    '{{if .State.Health}}{{range .State.Health.Log}}at={{.End}} exit={{.ExitCode}} output={{.Output}}{{end}}{{else}}healthcheck ausente{{end}}' "$container"
  diagnostic 'últimas 100 linhas do container (filtradas)' docker logs --tail 100 --timestamps "$container"
  diagnostic 'uso de armazenamento reportado pelo daemon Docker' docker system df
  diagnostic 'espaço do agente Jenkins (pode diferir do host Docker)' df -h
  diagnostic 'inodes do agente Jenkins (pode diferir do host Docker)' df -i
}
timeout="${1:-180}"
project="${2:-bookrush}"
recover="${3:-false}"
[[ "$timeout" =~ ^[1-9][0-9]*$ ]] || { echo 'Timeout de storage inválido.' >&2; exit 2; }
[[ "$recover" == true || "$recover" == false ]] || { echo 'Opção de recuperação inválida.' >&2; exit 2; }
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
    exited\|starting|exited\|unhealthy|exited\|healthy)
      if [[ "$recover" != true || "$attempt" != 1 ]]; then break; fi
      diagnostic 'estado anterior à tentativa única de recuperação' docker inspect --format \
        'storage={{.Name}} state={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} finished={{.State.FinishedAt}}' "$container"
      echo "SeaweedFS: tentando iniciar uma única vez o container existente $container; imagem, configuração e volume preservados." >&2
      if ! command -v timeout >/dev/null 2>&1; then
        echo 'Recuperação bloqueada: timeout indisponível no agente.' >&2
        break
      fi
      if ! timeout 30 docker start "$container" 2>&1 | filter_diagnostics >&2; then
        echo 'SeaweedFS: falha ao iniciar o container existente.' >&2
        break
      fi
      ;;
    running\|starting|running\|unhealthy) ;;
    *) break ;;
  esac
  if ((attempt < attempts)); then sleep 2; fi
done
failure_diagnostics "$container"
echo 'SeaweedFS indisponível: deploy bloqueado sem alterar volumes. Confira healthcheck, espaço em disco e a pipeline bookrush-seaweedfs; não remova o volume persistente.' >&2
exit 1
