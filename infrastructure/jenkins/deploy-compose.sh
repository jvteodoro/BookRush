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
rollback() {
    docker inspect --format 'Backend state={{.State.Status}} exitCode={{.State.ExitCode}} error={{.State.Error}}' bookrush-catalog-service-1 >&2 || true
  echo 'Deploy falhou; restaurando as imagens anteriores.' >&2
    if [[ -n "$old_ingestion" ]]; then
      BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" INGESTION_IMAGE="$old_ingestion" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service frontend book-ingestion-service
    else
      BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service frontend
    fi
}
trap 'rollback' ERR
# Atualiza as APIs e o frontend; não reinicia o Jenkins nem os serviços de dados.
"${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service book-ingestion-service frontend
"${compose[@]}" exec -T frontend wget -q -O - http://127.0.0.1:8080/api/status
trap - ERR
