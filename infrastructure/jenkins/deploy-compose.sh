#!/usr/bin/env bash
set -Eeuo pipefail
: "${BACKEND_IMAGE:?Imagem backend ausente}"
: "${FRONTEND_IMAGE:?Imagem frontend ausente}"
: "${INGESTION_IMAGE:?Imagem ingestion ausente}"
: "${ANALYTICS_IMAGE:?Imagem analytics ausente}"
if [[ -z "${ANALYTICS_RUNTIME_IMAGE:-}" ]]; then
  export ANALYTICS_RUNTIME_IMAGE="${ANALYTICS_IMAGE/book-analytics-service/book-analytics-runtime}"
fi
: "${ANALYTICS_RUNTIME_IMAGE:?Imagem analytics-runtime ausente}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/../.." && pwd)"

# Uma definição antiga do job pode não conhecer os serviços adicionados pela
# branch selecionada. Preserve registry/tag e construa a revisão do checkout,
# sem recorrer a imagens :local antigas do host.
compatibility_services=()
resolve_service_image() {
  local variable="$1" service="$2" image
  [[ -z "${!variable:-}" ]] || return 0
  [[ "$BACKEND_IMAGE" == */bookrush/catalog-service:* ]] || {
    echo "Não foi possível derivar $variable de BACKEND_IMAGE." >&2
    exit 1
  }
  image="${BACKEND_IMAGE%/bookrush/catalog-service:*}/bookrush/$service:${BACKEND_IMAGE##*:}"
  export "$variable=$image"
  compatibility_services+=("$service|$image")
}
resolve_service_image RECOMMENDATION_IMAGE recommendation-service
resolve_service_image READER_BFF_IMAGE reader-bff-service
resolve_service_image READER_PROFILE_IMAGE reader-profile-service
resolve_service_image SOCIAL_IMAGE social-service

# O job parametrizado pode carregar o Jenkinsfile da main antes de fazer
# checkout de uma feature. Durante a transição para uma pipeline que publica as
# imagens administrativas, derive os nomes e construa apenas o que estiver
# ausente no daemon usado pelo deploy.
if [[ -z "${ADMIN_SERVICE_IMAGE:-}" ]]; then
  ADMIN_SERVICE_IMAGE="${BACKEND_IMAGE/bookrush\/catalog-service/bookrush\/admin-service}"
  [[ "$ADMIN_SERVICE_IMAGE" != "$BACKEND_IMAGE" ]] || { echo 'Não foi possível derivar ADMIN_SERVICE_IMAGE de BACKEND_IMAGE.' >&2; exit 1; }
  export ADMIN_SERVICE_IMAGE
fi
if [[ -z "${ADMIN_WEB_IMAGE:-}" ]]; then
  ADMIN_WEB_IMAGE="${FRONTEND_IMAGE/bookrush\/frontend/bookrush\/admin-web}"
  [[ "$ADMIN_WEB_IMAGE" != "$FRONTEND_IMAGE" ]] || { echo 'Não foi possível derivar ADMIN_WEB_IMAGE de FRONTEND_IMAGE.' >&2; exit 1; }
  export ADMIN_WEB_IMAGE
fi
: "${ADMIN_SERVICE_IMAGE:?Imagem admin-service ausente}"
: "${ADMIN_WEB_IMAGE:?Imagem admin-web ausente}"
compose=(docker compose --project-name bookrush -f /opt/bookrush/compose.yaml -f "${script_dir}/deploy-images.compose.yaml" --env-file /run/bookrush.env --profile auth)
if [[ "${ANALYTICS_GPU:-false}" == "true" ]]; then
  compose+=( -f "${script_dir}/../compose.analytics-gpu.yaml" )
  export ANALYTICS_NLI_ENABLED=true
  export ANALYTICS_NLI_BATCH_SIZE="${ANALYTICS_NLI_BATCH_SIZE:-8}"
  export ANALYTICS_EMBEDDINGS_BATCH_SIZE="${ANALYTICS_EMBEDDINGS_BATCH_SIZE:-4}"
fi
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
for entry in "${compatibility_services[@]}"; do
  service="${entry%%|*}"
  image="${entry#*|}"
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "Imagem $image ausente; construindo compatibilidade do $service." >&2
    docker build -t "$image" -f "$repo_dir/services/$service/Dockerfile" "$repo_dir"
  fi
done
if ! docker image inspect "$ADMIN_SERVICE_IMAGE" >/dev/null 2>&1; then
  echo "Imagem $ADMIN_SERVICE_IMAGE ausente; construindo compatibilidade do admin-service." >&2
  docker build -t "$ADMIN_SERVICE_IMAGE" -f "$repo_dir/services/admin-service/Dockerfile" "$repo_dir"
fi
if ! docker image inspect "$ADMIN_WEB_IMAGE" >/dev/null 2>&1; then
  echo "Imagem $ADMIN_WEB_IMAGE ausente; construindo compatibilidade do admin-web." >&2
  docker build -t "$ADMIN_WEB_IMAGE" "$repo_dir/admin-web"
fi
old_backend="$(docker inspect --format '{{.Config.Image}}' bookrush-catalog-service-1 2>/dev/null || true)"
old_frontend="$(docker inspect --format '{{.Config.Image}}' bookrush-frontend-1 2>/dev/null || true)"
old_ingestion="$(docker inspect --format '{{.Config.Image}}' bookrush-book-ingestion-service-1 2>/dev/null || true)"
old_analytics="$(docker inspect --format '{{.Config.Image}}' bookrush-book-analytics-service-1 2>/dev/null || true)"
old_analytics_runtime="$(docker inspect --format '{{.Config.Image}}' bookrush-book-analytics-runtime-1 2>/dev/null || true)"
old_recommendation="$(docker inspect --format '{{.Config.Image}}' bookrush-recommendation-service-1 2>/dev/null || true)"
old_reader_bff="$(docker inspect --format '{{.Config.Image}}' bookrush-reader-bff-service-1 2>/dev/null || true)"
old_reader_profile="$(docker inspect --format '{{.Config.Image}}' bookrush-reader-profile-service-1 2>/dev/null || true)"
old_social="$(docker inspect --format '{{.Config.Image}}' bookrush-social-service-1 2>/dev/null || true)"
old_admin_service="$(docker inspect --format '{{.Config.Image}}' bookrush-admin-service-1 2>/dev/null || true)"
old_admin_web="$(docker inspect --format '{{.Config.Image}}' bookrush-admin-web-1 2>/dev/null || true)"
keycloak_state="$(docker inspect --format '{{.State.Status}}' bookrush-keycloak-1 2>/dev/null || true)"
diagnose() {
  for service in bookrush-keycloak-1 bookrush-catalog-service-1 bookrush-book-ingestion-service-1 bookrush-book-analytics-service-1 bookrush-book-analytics-runtime-1 bookrush-recommendation-service-1 bookrush-reader-bff-service-1 bookrush-reader-profile-service-1 bookrush-social-service-1 bookrush-admin-service-1 bookrush-admin-web-1 bookrush-frontend-1; do
    echo "--- diagnóstico $service ---" >&2
    docker inspect --format 'state={{.State.Status}} exitCode={{.State.ExitCode}} error={{.State.Error}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} image={{.Config.Image}}' "$service" >&2 || true
    if [[ "$service" == bookrush-admin-service-1 ]]; then
      # User-management failures can emit one Spring stack trace per probe.
      # Keep enough context to include the exception header and its root cause.
      docker logs --since=15m --tail=1200 --timestamps "$service" >&2 || true
    else
      docker logs --tail=120 --timestamps "$service" >&2 || true
    fi
  done
}
compose_services=(reverse-proxy catalog-service book-ingestion-service book-analytics-runtime book-analytics-service recommendation-service reader-bff-service reader-profile-service social-service admin-service admin-web frontend)
if [[ -n "$keycloak_state" ]]; then
  if [[ "$keycloak_state" != running ]]; then
    docker start bookrush-keycloak-1 >/dev/null
  fi
  keycloak_networked="$(docker network inspect bookrush_bookrush --format '{{range .Containers}}{{if eq .Name \"bookrush-keycloak-1\"}}yes{{end}}{{end}}' 2>/dev/null || true)"
  if [[ -z "$keycloak_networked" ]]; then
    # A stale Compose view can race with the daemon's endpoint list. If the
    # endpoint already exists, the container is effectively connected and the
    # deploy must continue without trying to recreate Keycloak.
    docker network connect bookrush_bookrush bookrush-keycloak-1 2>&1 || true
  fi
  echo 'Keycloak persistente existente detectado; preservando o container durante este deploy.' >&2
else
  compose_services=(keycloak "${compose_services[@]}")
fi

# Reconcile may create or rotate the credential in a persistent Keycloak. Read
# the live client secret after reconcile whenever administrator credentials are
# available; an env-file value is only a fallback. Never print either value.
xtrace_was_enabled=false
[[ $- == *x* ]] && xtrace_was_enabled=true
set +x
configured_admin_client_secret="${KEYCLOAK_ADMIN_CLIENT_SECRET:-}"
if [[ -z "$configured_admin_client_secret" ]]; then
  configured_admin_client_secret="$(awk -F= '$1 == "KEYCLOAK_ADMIN_CLIENT_SECRET" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
fi
configured_behavior_s2s_client_secret="${BEHAVIOR_S2S_CLIENT_SECRET:-}"
if [[ -z "$configured_behavior_s2s_client_secret" ]]; then
  configured_behavior_s2s_client_secret="$(awk -F= '$1 == "BEHAVIOR_S2S_CLIENT_SECRET" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
fi
keycloak_admin_user="$(awk -F= '$1 == "KEYCLOAK_CONFIG_CLI_ADMIN_USER" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
keycloak_admin_password="$(awk -F= '$1 == "KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
keycloak_admin_user="${keycloak_admin_user:-admin}"
admin_web_admin_username="$(awk -F= '$1 == "ADMIN_WEB_ADMIN_USERNAME" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
admin_web_admin_username="${admin_web_admin_username:-$keycloak_admin_user}"
# The admin-service manages product users. Keep every step (role repair,
# secret lookup and Compose interpolation) pinned to that same realm/client;
# stale protected-env values must not redirect only the application to the
# platform realm after the product-realm preflight has succeeded.
KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token
KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush
KEYCLOAK_ADMIN_CLIENT_ID=bookrush-admin-service
export KEYCLOAK_ADMIN_TOKEN_URI KEYCLOAK_ADMIN_BASE KEYCLOAK_ADMIN_CLIENT_ID
live_admin_client_secret=""
live_behavior_s2s_client_secret=""
if [[ -n "$keycloak_admin_password" ]]; then
  # config-cli can leave role mappings unchanged for an existing service
  # account. Reapply the minimum product-realm roles immediately before this
  # revision starts, without rotating credentials or touching users/sessions.
  docker run --rm -i --network bookrush_bookrush \
    -e KEYCLOAK_URL=http://keycloak:8080 \
    -e KEYCLOAK_USER="$keycloak_admin_user" \
    -e KEYCLOAK_PASSWORD="$keycloak_admin_password" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/grant-user-management-roles.py"
  docker run --rm -i --network bookrush_bookrush \
    -e KEYCLOAK_URL=http://keycloak:8080 \
    -e KEYCLOAK_USER="$keycloak_admin_user" \
    -e KEYCLOAK_PASSWORD="$keycloak_admin_password" \
    -e ADMIN_WEB_ADMIN_USERNAME="$admin_web_admin_username" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/grant-admin-console-access.py"
  live_admin_client_secret="$(docker run --rm -i --network bookrush_bookrush \
    -e KEYCLOAK_URL=http://keycloak:8080 \
    -e KEYCLOAK_REALM=bookrush \
    -e KEYCLOAK_CLIENT_ID="$KEYCLOAK_ADMIN_CLIENT_ID" \
    -e KEYCLOAK_USER="$keycloak_admin_user" \
    -e KEYCLOAK_PASSWORD="$keycloak_admin_password" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/read-client-secret.py" 2>/dev/null || true)"
  live_behavior_s2s_client_secret="$(docker run --rm -i --network bookrush_bookrush \
    -e KEYCLOAK_URL=http://keycloak:8080 \
    -e KEYCLOAK_REALM=bookrush-platform \
    -e KEYCLOAK_CLIENT_ID=bookrush-recommendation-behavior-s2s \
    -e KEYCLOAK_USER="$keycloak_admin_user" \
    -e KEYCLOAK_PASSWORD="$keycloak_admin_password" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/read-client-secret.py" 2>/dev/null || true)"
fi
KEYCLOAK_ADMIN_CLIENT_SECRET="${live_admin_client_secret:-$configured_admin_client_secret}"
BEHAVIOR_S2S_CLIENT_SECRET="${live_behavior_s2s_client_secret:-$configured_behavior_s2s_client_secret}"
if [[ "${FEED_PERSONALIZED_MODE:-OFF}" == ON && -z "$BEHAVIOR_S2S_CLIENT_SECRET" ]]; then
  echo 'Não foi possível resolver o segredo vigente do cliente S2S do behavior-service.' >&2
  exit 1
fi
unset keycloak_admin_password admin_web_admin_username live_admin_client_secret live_behavior_s2s_client_secret configured_admin_client_secret configured_behavior_s2s_client_secret
[[ -n "$KEYCLOAK_ADMIN_CLIENT_SECRET" ]] || { echo 'Não foi possível resolver o segredo vigente do admin-service no Keycloak.' >&2; exit 1; }
export KEYCLOAK_ADMIN_CLIENT_SECRET
export BEHAVIOR_S2S_CLIENT_SECRET
if $xtrace_was_enabled; then set -x; fi

probe_user_management() {
  local xtrace_was_enabled=false admin_user admin_password probe_secret probe_token
  local attempt status
  [[ $- == *x* ]] && xtrace_was_enabled=true
  set +x
  admin_user="$(awk -F= '$1 == "KEYCLOAK_CONFIG_CLI_ADMIN_USER" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
  admin_password="$(awk -F= '$1 == "KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" {sub(/^[^=]*=/, ""); print; exit}' /run/bookrush.env 2>/dev/null || true)"
  admin_user="${admin_user:-admin}"
  if [[ -z "$admin_password" ]]; then
    echo 'Smoke de usuários: KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD ausente.' >&2
    return 1
  fi
  for attempt in {1..6}; do
    probe_secret="$(docker run --rm -i --network bookrush_bookrush \
      -e KEYCLOAK_URL=http://keycloak:8080 \
      -e KEYCLOAK_REALM=bookrush-platform \
      -e KEYCLOAK_CLIENT_ID=bookrush-ingestion-admin \
      -e KEYCLOAK_USER="$admin_user" \
      -e KEYCLOAK_PASSWORD="$admin_password" \
      python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/read-client-secret.py" 2>/dev/null)" && break
    echo "Smoke de usuários: Keycloak ainda indisponível para resolver o cliente (tentativa $attempt/6)." >&2
    sleep 5
  done
  if [[ -z "$probe_secret" ]]; then
    echo 'Smoke de usuários: não foi possível resolver o cliente técnico.' >&2
    return 1
  fi
  for attempt in {1..6}; do
    probe_token="$(docker run --rm --network bookrush_bookrush curlimages/curl:8.10.1 -fsS -X POST \
      http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token \
      -d grant_type=client_credentials -d client_id=bookrush-ingestion-admin \
      --data-urlencode "client_secret=$probe_secret" 2>/dev/null | jq -er '.access_token' 2>/dev/null)" && break
    echo "Smoke de usuários: token técnico ainda indisponível (tentativa $attempt/6)." >&2
    sleep 5
  done
  if [[ -z "$probe_token" ]]; then
    echo 'Smoke de usuários: o Keycloak não emitiu o token técnico.' >&2
    return 1
  fi
  status=000
  for attempt in {1..12}; do
    # Exercise the same-origin proxy shipped in the admin-web image from this
    # revision. The host Traefik file comes from a stable checkout outside the
    # Jenkins workspace and must not make a feature deploy depend on stale
    # host files.
    status="$(docker run --rm --network bookrush_bookrush curlimages/curl:8.10.1 \
      -sS -o /dev/null -w '%{http_code}' \
      -H "Authorization: Bearer $probe_token" \
      'http://admin-web/api/v1/admin/users?page=0&size=1' 2>/dev/null || true)"
    [[ "$status" == 200 ]] && break
    echo "Smoke de usuários: proxy do admin-web retornou HTTP ${status:-000} (tentativa $attempt/12)." >&2
    [[ "$status" == 401 || "$status" == 403 ]] && break
    sleep 5
  done
  unset admin_password probe_secret probe_token
  if $xtrace_was_enabled; then set -x; fi
  [[ "$status" == 200 ]]
}

verify_admin_runtime_config() {
  local xtrace_was_enabled=false runtime_env runtime_token_uri runtime_admin_base
  local runtime_client_id runtime_client_secret
  [[ $- == *x* ]] && xtrace_was_enabled=true
  set +x
  runtime_env="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' bookrush-admin-service-1)"
  runtime_token_uri="$(awk -F= '$1 == "KEYCLOAK_ADMIN_TOKEN_URI" {sub(/^[^=]*=/, ""); print; exit}' <<<"$runtime_env")"
  runtime_admin_base="$(awk -F= '$1 == "KEYCLOAK_ADMIN_BASE" {sub(/^[^=]*=/, ""); print; exit}' <<<"$runtime_env")"
  runtime_client_id="$(awk -F= '$1 == "KEYCLOAK_ADMIN_CLIENT_ID" {sub(/^[^=]*=/, ""); print; exit}' <<<"$runtime_env")"
  runtime_client_secret="$(awk -F= '$1 == "KEYCLOAK_ADMIN_CLIENT_SECRET" {sub(/^[^=]*=/, ""); print; exit}' <<<"$runtime_env")"
  unset runtime_env
  local matches=true
  [[ "$runtime_token_uri" == "$KEYCLOAK_ADMIN_TOKEN_URI" ]] || matches=false
  [[ "$runtime_admin_base" == "$KEYCLOAK_ADMIN_BASE" ]] || matches=false
  [[ "$runtime_client_id" == "$KEYCLOAK_ADMIN_CLIENT_ID" ]] || matches=false
  [[ -n "$runtime_client_secret" && "$runtime_client_secret" == "$KEYCLOAK_ADMIN_CLIENT_SECRET" ]] || matches=false
  unset runtime_client_secret
  if $xtrace_was_enabled; then set -x; fi
  if ! $matches; then
    echo "Configuração Keycloak divergente no admin-service (tokenUri=$runtime_token_uri adminBase=$runtime_admin_base clientId=$runtime_client_id)." >&2
    return 1
  fi
  echo 'Configuração Keycloak efetiva do admin-service: OK' >&2
}

probe_catalog() {
  local attempt
  for attempt in {1..12}; do
    if "${compose[@]}" exec -T frontend wget -q -O /dev/null http://127.0.0.1:8080/api/status; then
      return 0
    fi
    echo "Smoke do catálogo: frontend ainda indisponível (tentativa $attempt/12)." >&2
    sleep 5
  done
  return 1
}

rollback() {
  echo 'Deploy falhou; restaurando as imagens anteriores.' >&2
  if [[ -n "$old_ingestion" && -n "$old_analytics" && -n "$old_analytics_runtime" && -n "$old_recommendation" && -n "$old_reader_bff" && -n "$old_reader_profile" && -n "$old_social" && -n "$old_admin_service" && -n "$old_admin_web" ]]; then
    BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" INGESTION_IMAGE="$old_ingestion" ANALYTICS_IMAGE="$old_analytics" ANALYTICS_RUNTIME_IMAGE="$old_analytics_runtime" RECOMMENDATION_IMAGE="$old_recommendation" READER_BFF_IMAGE="$old_reader_bff" READER_PROFILE_IMAGE="$old_reader_profile" SOCIAL_IMAGE="$old_social" ADMIN_SERVICE_IMAGE="$old_admin_service" ADMIN_WEB_IMAGE="$old_admin_web" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${compose_services[@]}"
  elif [[ -n "$old_backend" && -n "$old_frontend" ]]; then
    BACKEND_IMAGE="$old_backend" FRONTEND_IMAGE="$old_frontend" "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 reverse-proxy catalog-service frontend
  else
    echo 'Não há imagens anteriores completas para rollback; mantendo a revisão solicitada para recuperar o stack.' >&2
    "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${compose_services[@]}"
  fi
}

echo "Iniciando deploy das imagens:" >&2
printf '  %s\n' "${BACKEND_IMAGE}" "${INGESTION_IMAGE}" "${ANALYTICS_RUNTIME_IMAGE}" "${ANALYTICS_IMAGE}" "${RECOMMENDATION_IMAGE}" "${READER_BFF_IMAGE}" "${READER_PROFILE_IMAGE}" "${SOCIAL_IMAGE}" "${ADMIN_SERVICE_IMAGE}" "${ADMIN_WEB_IMAGE}" "${FRONTEND_IMAGE}" >&2

# Não usar somente trap ERR: docker compose pode falhar durante --wait e o
# diagnóstico precisa ocorrer antes do rollback, preservando o exit code.
if ! "${compose[@]}" up -d --no-build --no-deps --force-recreate --wait --wait-timeout 180 "${compose_services[@]}"; then
  echo 'Falha ao iniciar ou aguardar os serviços novos.' >&2
  diagnose
  rollback
  echo 'Rollback concluído; o deploy será marcado como falho.' >&2
  exit 1
fi

if ! verify_admin_runtime_config; then
  diagnose
  rollback
  echo 'Rollback concluído; o deploy será marcado como falho.' >&2
  exit 1
fi

if ! probe_user_management; then
  echo 'Smoke test autenticado de gerenciamento de usuários falhou.' >&2
  diagnose
  rollback
  echo 'Rollback concluído; o deploy será marcado como falho.' >&2
  exit 1
fi

if ! probe_catalog; then
  echo 'Smoke test do catálogo pelo frontend falhou.' >&2
  diagnose
  rollback
  echo 'Rollback concluído; o deploy será marcado como falho.' >&2
  exit 1
fi
echo 'Deploy e smoke test concluídos.' >&2
