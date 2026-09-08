#!/usr/bin/env bash
set -Eeuo pipefail
: "${BACKEND_IMAGE:?Imagem backend ausente}"
: "${FRONTEND_IMAGE:?Imagem frontend ausente}"
compose=(docker compose --project-name bookrush -f /opt/bookrush/compose.yaml --env-file /run/bookrush.env)
old_backend="$(docker inspect --format '{{.Config.Image}}' bookrush-catalog-service-1)"
old_frontend="$(docker inspect --format '{{.Config.Image}}' bookrush-frontend-1)"
rollback() {
    echo 'Deploy falhou; restaurando as imagens anteriores.' >&2
    BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 catalog-service frontend
}
trap 'rollback' ERR
# Apenas a API e o frontend; não reinicia o Jenkins nem os serviços de dados.
"${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 catalog-service frontend
"${compose[@]}" exec -T frontend wget -q -O - http://127.0.0.1:8080/api/status
trap - ERR
