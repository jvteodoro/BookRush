#!/usr/bin/env bash
set -Eeuo pipefail

# End-to-end proof of platform token -> admin-service -> product-realm users.
# All containers, credentials and data are disposable.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-admin-e2e.XXXXXX")"
suffix="$RANDOM-$$"
network="bookrush-admin-e2e-$suffix"
keycloak="bookrush-admin-e2e-keycloak-$suffix"
postgres="bookrush-admin-e2e-postgres-$suffix"
service="bookrush-admin-e2e-service-$suffix"
social_service="bookrush-admin-e2e-social-$suffix"
web="bookrush-admin-e2e-web-$suffix"
probe="bookrush-admin-e2e-probe-$suffix"
admin_password='disposable-admin-password'
human_password='disposable-human-password'
human_username='disposable-platform-admin'
product_secret='disposable-product-client-secret'
platform_secret='disposable-platform-client-secret'
image="bookrush/admin-service-e2e:$suffix"
social_image="bookrush/social-service-e2e:$suffix"
web_image="bookrush/admin-web-e2e:$suffix"
cleanup() {
  local rc=$?
  docker rm -f "$probe" "$web" "$service" "$social_service" "$keycloak" "$postgres" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$rc"
}
trap cleanup EXIT

docker run --rm --user "$(id -u):$(id -g)" \
  -e KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" \
  -e INGESTION_ADMIN_CLIENT_SECRET="$platform_secret" \
  -v "$repo_dir:/repo:ro" -v "$work_dir:/out" \
  python:3.12-slim python3 /repo/infrastructure/keycloak/render-config.py \
  /repo/infrastructure/keycloak /out/rendered

docker network create "$network" >/dev/null
docker run -d --name "$postgres" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=bookrush -e POSTGRES_USER=bookrush -e POSTGRES_PASSWORD=bookrush \
  postgres:16-alpine >/dev/null
# Reproduce the Compose deployment order that exposed the production race:
# admin-web must remain running even while admin-service has no DNS record yet.
docker build -q -t "$web_image" "$repo_dir/admin-web" >/dev/null
docker run -d --name "$web" --network "$network" --network-alias admin-web \
  "$web_image" >/dev/null
sleep 1
[[ "$(docker inspect --format '{{.State.Running}}' "$web")" == true ]] || {
  docker logs "$web" >&2
  echo 'Admin web encerrou antes de o admin-service entrar na rede.' >&2
  exit 1
}
docker create --name "$keycloak" --network "$network" --network-alias keycloak \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /tmp/realms/*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm' >/dev/null
docker cp "$work_dir/rendered" "$keycloak:/tmp/realms"
docker start "$keycloak" >/dev/null
probe="$(docker run -d --name "$probe" --network "$network" curlimages/curl:8.10.1 tail -f /dev/null)"
for _ in $(seq 1 120); do
  docker exec "$probe" curl -fsS http://keycloak:8080/realms/bookrush-platform/.well-known/openid-configuration >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$probe" curl -fsS http://keycloak:8080/realms/bookrush/.well-known/openid-configuration >/dev/null

# Match hardened/persistent installations where assigned service-account roles
# are not automatically included in the token.
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://127.0.0.1:8080 --realm master --user admin \
  --password "$admin_password" >/dev/null
client_uuid="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients -r bookrush \
  -q clientId=bookrush-admin-service --fields id --format csv --noquotes)"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh update "clients/$client_uuid" \
  -r bookrush -s fullScopeAllowed=false >/dev/null

docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh create users -r bookrush-platform \
  -s username="$human_username" -s firstName=Disposable -s lastName=Administrator \
  -s email=disposable-platform-admin@bookrush.test -s enabled=true \
  -s emailVerified=true -s 'requiredActions=[]' >/dev/null
human_user_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get users \
  -r bookrush-platform -q username="$human_username" --fields id --format csv --noquotes)"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh update "users/$human_user_id" \
  -r bookrush-platform -s enabled=true -s emailVerified=true -s 'requiredActions=[]' >/dev/null
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh set-password -r bookrush-platform \
  --userid "$human_user_id" --new-password "$human_password" >/dev/null
docker run --rm -i --network "$network" \
  -e KEYCLOAK_URL=http://keycloak:8080 -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD="$admin_password" -e ADMIN_WEB_ADMIN_USERNAME="$human_username" \
  python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/grant-admin-console-access.py"
admin_web_client_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients \
  -r bookrush-platform -q clientId=labsoft-admin-web --fields id --format csv --noquotes)"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh update "clients/$admin_web_client_id" \
  -r bookrush-platform -s directAccessGrantsEnabled=true >/dev/null

# Exercise twice the same role repair used immediately before deployment,
# proving that it is safe against an already-reconciled persistent realm.
for _ in 1 2; do
  docker run --rm -i --network "$network" \
    -e KEYCLOAK_URL=http://keycloak:8080 -e KEYCLOAK_USER=admin \
    -e KEYCLOAK_PASSWORD="$admin_password" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/grant-user-management-roles.py"
done

docker build -q -t "$image" -f "$repo_dir/services/admin-service/Dockerfile" "$repo_dir" >/dev/null
docker build -q -t "$social_image" -f "$repo_dir/services/social-service/Dockerfile" "$repo_dir" >/dev/null
for _ in $(seq 1 60); do
  docker exec "$postgres" pg_isready -U bookrush -d bookrush >/dev/null 2>&1 && break
  sleep 1
done
docker run -d --name "$social_service" --network "$network" \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=bookrush \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/bookrush-platform \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-ingestion "$social_image" >/dev/null
for _ in $(seq 1 90); do
  docker exec "$probe" curl -fsS http://"$social_service":8096/actuator/health >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$probe" curl -fsS http://"$social_service":8096/actuator/health >/dev/null
docker run -d --name "$service" --network "$network" --network-alias admin-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=bookrush \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/bookrush-platform \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-ingestion \
  -e KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token \
  -e KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush \
  -e KEYCLOAK_ADMIN_CLIENT_ID=bookrush-admin-service \
  -e KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" "$image" >/dev/null
for _ in $(seq 1 90); do
  docker exec "$probe" curl -fsS http://admin-service:8100/actuator/health >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$probe" curl -fsS http://admin-service:8100/actuator/health >/dev/null

# Simulate a comment created before moderation state existed, plus the report
# that the reader flow persists in the shared social schema.
legacy_comment_id='11111111-1111-4111-8111-111111111111'
report_id='22222222-2222-4222-8222-222222222222'
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush >/dev/null <<SQL
CREATE SCHEMA IF NOT EXISTS social;
CREATE SCHEMA IF NOT EXISTS reader_profile;
CREATE TABLE IF NOT EXISTS reader_profile.profile (
  subject_key TEXT PRIMARY KEY, display_name TEXT, bio TEXT,
  is_public BOOLEAN NOT NULL DEFAULT true, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS social.comments (
  id UUID PRIMARY KEY, subject_key TEXT NOT NULL, book_id UUID NOT NULL,
  author_display_name TEXT, body TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS social.comment_moderation_state (
  comment_id UUID PRIMARY KEY REFERENCES social.comments(id),
  visibility VARCHAR(16) NOT NULL DEFAULT 'VISIBLE', reason_code VARCHAR(80),
  updated_by_subject TEXT, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS social.reports (
  id UUID PRIMARY KEY, reporter TEXT NOT NULL, book_id UUID, comment_id UUID,
  reason TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN',
  reviewed_by_subject TEXT, reviewed_at TIMESTAMPTZ, resolved_at TIMESTAMPTZ,
  resolution_note VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO reader_profile.profile(subject_key, display_name) VALUES
  ('reader-1', 'Ana Leitora'), ('reader-2', 'Bruno Denunciante');
INSERT INTO social.comments(id, subject_key, author_display_name, book_id, body)
VALUES ('$legacy_comment_id', 'reader-1', NULL, '33333333-3333-4333-8333-333333333333', 'legacy comment');
INSERT INTO social.reports(id, reporter, book_id, comment_id, reason)
VALUES ('$report_id', 'reader-2', '33333333-3333-4333-8333-333333333333', '$legacy_comment_id', 'spam');
SQL

token_response="$(docker exec "$probe" curl -fsS -X POST \
  http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token \
  -d grant_type=password -d client_id=labsoft-admin-web \
  --data-urlencode "username=$human_username" \
  --data-urlencode "password=$human_password")"
token="$(sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"\\]*\)".*/\1/p' <<<"$token_response")"
test -n "$token"
docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  set -eu
  payload="${ACCESS_TOKEN#*.}"
  payload="${payload%%.*}"
  payload="$(printf "%s" "$payload" | tr "_-" "/+" | base64 -d 2>/dev/null)"
  printf "%s" "$payload" | grep -q "\"iss\":\"http://keycloak:8080/realms/bookrush-platform\""
  printf "%s" "$payload" | grep -q '"sub"'
  printf "%s" "$payload" | grep -q "bookrush-ingestion"
  printf "%s" "$payload" | grep -q "platform-admins"
'
status=000
for _ in $(seq 1 30); do
  status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    'http://admin-web/api/v1/admin/users?page=0&size=1' 2>/dev/null || true)"
  [[ "$status" == 200 ]] && break
  sleep 1
done
if [[ "$status" != 200 ]]; then
  docker exec "$probe" curl -sS -D - -o /dev/null \
    -H "Authorization: Bearer $token" \
    'http://admin-web/api/v1/admin/users?page=0&size=1' >&2 || true
  docker logs --tail=160 "$service" >&2
  echo "Admin user management E2E retornou HTTP $status." >&2
  exit 1
fi

for visibility in HIDDEN REMOVED VISIBLE; do
  response="$(docker exec "$probe" curl -fsS -X POST \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data "{\"commentId\":\"$legacy_comment_id\",\"visibility\":\"$visibility\",\"reasonCode\":\"E2E_REVIEW\"}" \
    http://admin-web/api/v1/admin/moderation/comments)"
  [[ "$(jq -r '.visibility' <<<"$response")" == "$visibility" ]]
  listed_visibility="$(docker exec "$probe" curl -fsS \
    -H "Authorization: Bearer $token" \
    http://admin-web/api/v1/admin/moderation/comments \
    | jq -r --arg id "$legacy_comment_id" '.[] | select(.id == $id) | .visibility')"
  [[ "$listed_visibility" == "$visibility" ]]
done
docker exec "$probe" curl -fsS -H "Authorization: Bearer $token" \
  http://admin-web/api/v1/admin/reports \
  | jq -e --arg report "$report_id" --arg comment "$legacy_comment_id" \
      '.[] | select(.id == $report and .comment_id == $comment and .status == "OPEN" and .reporter_name == "Bruno Denunciante" and .comment_author_name == "Ana Leitora")' >/dev/null

for transition in 'IN_REVIEW|' 'RESOLVED|comment removed' 'DISMISSED|false positive' 'OPEN|'; do
  status_name="${transition%%|*}"
  status_note="${transition#*|}"
  response="$(docker exec "$probe" curl -fsS -X POST \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data "$(jq -nc --arg status "$status_name" --arg note "$status_note" '{status:$status,note:$note}')" \
    "http://admin-web/api/v1/admin/reports/$report_id/status")"
  [[ "$(jq -r '.status' <<<"$response")" == "$status_name" ]]
done
docker exec "$postgres" psql -At -U bookrush -d bookrush \
  -c "SELECT count(*) FROM admin.audit_log WHERE action='REPORT_STATUS_CHANGED' AND target='$report_id'" \
  | grep -qx '4'

echo 'Admin user management E2E: names, legacy comments, report lifecycle and audit OK'
