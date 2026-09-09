#!/usr/bin/env bash
set -Eeuo pipefail
: "${BACKEND_IMAGE:?Imagem backend ausente}"
: "${FRONTEND_IMAGE:?Imagem frontend ausente}"
: "${INGESTION_IMAGE:?Imagem ingestion ausente}"
compose=(docker compose --project-name bookrush -f /opt/bookrush/compose.yaml --env-file /run/bookrush.env)
storage_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' bookrush-seaweedfs-1 2>/dev/null || true)"
if [[ "$storage_health" != healthy ]]; then
  echo 'SeaweedFS não está provisionado/saudável. Execute o procedimento inicial de infraestrutura antes do deploy.' >&2
  exit 1
fi
old_backend="$(docker inspect --format '{{.Config.Image}}' bookrush-catalog-service-1)"
old_frontend="$(docker inspect --format '{{.Config.Image}}' bookrush-frontend-1)"
old_ingestion="$(docker inspect --format '{{.Config.Image}}' bookrush-book-ingestion-service-1 2>/dev/null || true)"
diagnose() {
  for service in bookrush-catalog-service-1 bookrush-book-ingestion-service-1 bookrush-frontend-1; do
    echo "--- diagnóstico $service ---" >&2
    docker inspect --format 'state={{.State.Status}} exitCode={{.State.ExitCode}} error={{.State.Error}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} image={{.Config.Image}}' "$service" >&2 || true
    docker logs --tail=120 --timestamps "$service" >&2 || true
  done
}
rollback() {
  echo 'Deploy falhou; restaurando as imagens anteriores.' >&2
    if [[ -n "$old_ingestion" ]]; then
      BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" INGESTION_IMAGE="$old_ingestion" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service frontend book-ingestion-service
    else
      BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service frontend
    fi
}

deploy_services=(reverse-proxy catalog-service book-ingestion-service frontend)
echo "Iniciando deploy das imagens:" >&2
printf '  %s\n' "${BACKEND_IMAGE}" "${INGESTION_IMAGE}" "${FRONTEND_IMAGE}" >&2

# Não usar somente trap ERR: docker compose pode falhar durante --wait e o
# diagnóstico precisa ocorrer antes do rollback, preservando o exit code.
if ! "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${deploy_services[@]}"; then
  echo 'Falha ao iniciar ou aguardar os serviços novos.' >&2
  diagnose
  rollback
  echo 'Rollback concluído; o deploy será marcado como falho.' >&2
  exit 1
fi

if ! "${compose[@]}" exec -T frontend wget -q -O - http://127.0.0.1:8080/api/status; then
  echo 'Smoke test do catálogo pelo frontend falhou.' >&2
  diagnose
  rollback
  echo 'Rollback concluído; o deploy será marcado como falho.' >&2
  exit 1
fi
echo 'Deploy e smoke test concluídos.' >&2
