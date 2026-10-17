#!/usr/bin/env bash
set -Eeuo pipefail
: "${BACKEND_IMAGE:?Imagem backend ausente}"
: "${FRONTEND_IMAGE:?Imagem frontend ausente}"
: "${INGESTION_IMAGE:?Imagem ingestion ausente}"
: "${ANALYTICS_IMAGE:?Imagem analytics ausente}"
: "${READER_PROFILE_IMAGE:?Imagem reader-profile ausente}"
: "${SOCIAL_IMAGE:?Imagem social ausente}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compose=(docker compose --project-name bookrush -f /opt/bookrush/compose.yaml -f "${script_dir}/deploy-images.compose.yaml" --env-file /run/bookrush.env --profile auth)
storage_public_endpoint="$(awk -F= '$1 == "STORAGE_PUBLIC_ENDPOINT" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
if [[ -z "$storage_public_endpoint" || "$storage_public_endpoint" =~ ^https?://(localhost|127\.0\.0\.1)(:|/|$) || "$storage_public_endpoint" != https://* ]]; then
  echo 'STORAGE_PUBLIC_ENDPOINT deve ser uma URL HTTPS pública do endpoint S3; localhost não pode ser usado em produção.' >&2
  exit 1
fi
storage_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' bookrush-seaweedfs-1 2>/dev/null || true)"
if [[ "$storage_health" != healthy ]]; then
  echo 'SeaweedFS não está provisionado/saudável. Execute o procedimento inicial de infraestrutura antes do deploy.' >&2
  exit 1
fi
old_backend="$(docker inspect --format '{{.Config.Image}}' bookrush-catalog-service-1 2>/dev/null || true)"
old_frontend="$(docker inspect --format '{{.Config.Image}}' bookrush-frontend-1 2>/dev/null || true)"
old_ingestion="$(docker inspect --format '{{.Config.Image}}' bookrush-book-ingestion-service-1 2>/dev/null || true)"
old_analytics="$(docker inspect --format '{{.Config.Image}}' bookrush-book-analytics-service-1 2>/dev/null || true)"
old_reader_profile="$(docker inspect --format '{{.Config.Image}}' bookrush-reader-profile-service-1 2>/dev/null || true)"
old_social="$(docker inspect --format '{{.Config.Image}}' bookrush-social-service-1 2>/dev/null || true)"
keycloak_state="$(docker inspect --format '{{.State.Status}}' bookrush-keycloak-1 2>/dev/null || true)"
diagnose() {
  for service in bookrush-keycloak-1 bookrush-catalog-service-1 bookrush-book-ingestion-service-1 bookrush-book-analytics-service-1 bookrush-reader-profile-service-1 bookrush-social-service-1 bookrush-frontend-1; do
    echo "--- diagnóstico $service ---" >&2
    docker inspect --format 'state={{.State.Status}} exitCode={{.State.ExitCode}} error={{.State.Error}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} image={{.Config.Image}}' "$service" >&2 || true
    docker logs --tail=120 --timestamps "$service" >&2 || true
  done
}
compose_services=(reverse-proxy catalog-service book-ingestion-service book-analytics-service reader-profile-service social-service frontend)
if [[ "$keycloak_state" == running && -n "$(docker network inspect bookrush_bookrush --format '{{range .Containers}}{{if eq .Name \"bookrush-keycloak-1\"}}yes{{end}}{{end}}' 2>/dev/null || true)" ]]; then
  echo 'Keycloak existente e ativo detectado; preservando o container persistente durante este deploy.' >&2
else
  compose_services=(keycloak "${compose_services[@]}")
fi

rollback() {
  echo 'Deploy falhou; restaurando as imagens anteriores.' >&2
  if [[ -n "$old_ingestion" && -n "$old_analytics" && -n "$old_reader_profile" && -n "$old_social" ]]; then
    BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" INGESTION_IMAGE="$old_ingestion" ANALYTICS_IMAGE="$old_analytics" READER_PROFILE_IMAGE="$old_reader_profile" SOCIAL_IMAGE="$old_social" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${compose_services[@]}"
  elif [[ -n "$old_backend" && -n "$old_frontend" ]]; then
    BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service frontend
  else
    echo 'Não há imagens anteriores completas para rollback; mantendo a revisão solicitada para recuperar o stack.' >&2
    "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${compose_services[@]}"
  fi
}

echo "Iniciando deploy das imagens:" >&2
printf '  %s\n' "${BACKEND_IMAGE}" "${INGESTION_IMAGE}" "${ANALYTICS_IMAGE}" "${READER_PROFILE_IMAGE}" "${SOCIAL_IMAGE}" "${FRONTEND_IMAGE}" >&2

# Não usar somente trap ERR: docker compose pode falhar durante --wait e o
# diagnóstico precisa ocorrer antes do rollback, preservando o exit code.
if ! "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${compose_services[@]}"; then
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
