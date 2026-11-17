#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "E2E failure at line %s\n" "$LINENO" >&2' ERR

# End-to-end proof of platform token -> admin-service -> product-realm users.
# All containers, credentials and data are disposable.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
suffix="$RANDOM-$$"
network="bookrush-admin-e2e-$suffix"
keycloak="bookrush-admin-e2e-keycloak-$suffix"
postgres="bookrush-admin-e2e-postgres-$suffix"
service="bookrush-admin-e2e-service-$suffix"
social_service="bookrush-admin-e2e-social-$suffix"
product_social="bookrush-comment-author-e2e-$suffix"
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
  docker rm -fv "$probe" "$web" "$service" "$social_service" "$product_social" "$keycloak" "$postgres" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  # Remove only this invocation's unique image tags; retain shared build cache.
  docker image rm "$image" "$social_image" "$web_image" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$rc"
}
trap cleanup EXIT

if docker inspect "$HOSTNAME" >/dev/null 2>&1; then
  work_dir="$(mktemp -d "$repo_dir/.bookrush-admin-e2e.XXXXXX")"
  docker run --rm --user "$(id -u):$(id -g)" --volumes-from "$HOSTNAME" \
    -w "$repo_dir" \
    -e KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" \
    -e INGESTION_ADMIN_CLIENT_SECRET="$platform_secret" \
    python:3.12-slim python3 infrastructure/keycloak/render-config.py \
    infrastructure/keycloak "$work_dir/rendered"
else
  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-admin-e2e.XXXXXX")"
  docker run --rm --user "$(id -u):$(id -g)" \
    -e KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" \
    -e INGESTION_ADMIN_CLIENT_SECRET="$platform_secret" \
    -v "$repo_dir:/repo:ro" -v "$work_dir:/out" \
    python:3.12-slim python3 /repo/infrastructure/keycloak/render-config.py \
    /repo/infrastructure/keycloak /out/rendered
fi

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
docker exec "$postgres" createdb -U bookrush reporting_upgrade
mapfile -t admin_migrations < <(
  printf '%s\n' "$repo_dir"/services/admin-service/src/main/resources/db/migration/V[0-9]*__*.sql | sort -V
)
for migration in "${admin_migrations[@]}"; do
  [[ "$migration" == *"/V27__"* || "$migration" == *"/V28__"* || "$migration" == *"/V29__"* || "$migration" == *"/V30__"* || "$migration" == *"/V31__"* || "$migration" == *"/V32__"* || "$migration" == *"/V33__"* || "$migration" == *"/V34__"* || "$migration" == *"/V35__"* || "$migration" == *"/V36__"* || "$migration" == *"/V37__"* || "$migration" == *"/V38__"* ]] && continue
  docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
    < "$migration" >/dev/null
done
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V27__reporting_ingestion_queue_snapshot.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V28__reporting_content_coverage.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V29__reporting_content_rank_score.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V30__reporting_excerpt_production.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V31__reporting_import_quality_daily.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V32__reporting_backlog_snapshot.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V33__reporting_editorial_submission_funnel.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V34__reporting_rights_status_daily.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V35__reporting_social_behavior_events.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V36__reading_progress_cohorts.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V37__reading_session_duration_buckets.sql" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d reporting_upgrade \
  < "$repo_dir/services/admin-service/src/main/resources/db/migration/V38__reporting_library_state_snapshot.sql" >/dev/null
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='ingestion_queue_snapshot'" | grep -qx 8
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_ingestion_queue_time_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V26 -> V27 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='content_coverage_snapshot'" | grep -qx 15
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_content_coverage_time_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V27 -> V28 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='content_rank_score_snapshot'" | grep -qx 13
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_content_rank_score_time_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V28 -> V29 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='excerpt_production_snapshot'" | grep -qx 10
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_excerpt_production_time_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V29 -> V30 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='import_quality_daily'" | grep -qx 11
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_import_quality_day_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V30 -> V31 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='backlog_snapshot'" | grep -qx 13
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_backlog_time_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V31 -> V32 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='editorial_submission_funnel_daily'" | grep -qx 10
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_editorial_funnel_cohort_day_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V32 -> V33 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='rights_status_daily'" | grep -qx 9
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='reporting' AND indexname='reporting_rights_status_day_idx'" | grep -qx 1
echo 'Admin reporting migration upgrade E2E: V33 -> V34 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='social_daily'" | grep -qx 21
echo 'Admin reporting migration upgrade E2E: V34 -> V35 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='reading_progress_daily'" | grep -qx 12
echo 'Admin reporting migration upgrade E2E: V35 -> V36 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='reading_daily' AND column_name IN ('session_under_1m','session_1m_5m','session_5m_15m','session_15m_plus')" | grep -qx 4
echo 'Admin reporting migration upgrade E2E: V36 -> V37 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='reporting' AND table_name='library_state_snapshot'" | grep -qx 7
echo 'Admin reporting migration upgrade E2E: V37 -> V38 OK'
docker exec "$postgres" psql -At -U bookrush -d reporting_upgrade \
  -c "SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='reporting' AND table_name='projection_health' AND column_name='definition_version'" | grep -qx 120
echo 'Admin reporting migration upgrade E2E: projection definition version supports 120 characters'
# Real PostgreSQL upgrade from V7, with retained comment and moderation evidence.
docker exec "$postgres" createdb -U bookrush comments_upgrade
for migration in "$repo_dir"/services/social-service/src/main/resources/db/migration/V[1-7]__*.sql; do
  docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d comments_upgrade < "$migration" >/dev/null
done
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d comments_upgrade >/dev/null <<'SQL'
INSERT INTO social.comments(id, subject_key, book_id, body)
VALUES ('88888888-8888-4888-8888-888888888888', 'upgrade-reader', '33333333-3333-4333-8333-333333333333', 'migration fixture');
INSERT INTO social.comment_moderation_state(comment_id, visibility, reason_code)
VALUES ('88888888-8888-4888-8888-888888888888', 'HIDDEN', 'upgrade evidence');
SQL
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d comments_upgrade \
  < "$repo_dir/services/social-service/src/main/resources/db/migration/V8__comment_author_deletion.sql" >/dev/null
docker exec "$postgres" psql -At -U bookrush -d comments_upgrade \
  -c "SELECT c.author_deleted_at IS NULL AND c.body='migration fixture' AND m.visibility='HIDDEN' FROM social.comments c JOIN social.comment_moderation_state m ON m.comment_id=c.id" | grep -qx t
# V9 must preserve existing follow relations while adding its incoming index.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d comments_upgrade \
  -c "INSERT INTO social.follows(follower,followed) VALUES ('upgrade-reader','legacy-target')" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d comments_upgrade \
  < "$repo_dir/services/social-service/src/main/resources/db/migration/V9__follower_count_index.sql" >/dev/null
docker exec "$postgres" psql -At -U bookrush -d comments_upgrade \
  -c "SELECT count(*) FROM social.follows WHERE followed='legacy-target'" | grep -qx 1
docker exec "$postgres" psql -At -U bookrush -d comments_upgrade \
  -c "SELECT count(*) FROM pg_indexes WHERE schemaname='social' AND indexname='social_follows_followed_idx'" | grep -qx 1
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
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush >/dev/null <<'SQL'
CREATE SCHEMA behavior;
CREATE TABLE behavior.events (
  id UUID PRIMARY KEY, event_key TEXT UNIQUE, occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(), identity_issuer TEXT,
  identity_subject TEXT, book_id UUID, event_type TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE behavior.rejects (
  id UUID PRIMARY KEY, received_at TIMESTAMPTZ NOT NULL DEFAULT now(), reason TEXT NOT NULL
);
CREATE TABLE behavior.outbox (
  id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE behavior.daily_book (
  day DATE NOT NULL, book_id UUID NOT NULL,
  impressions BIGINT NOT NULL DEFAULT 0, opens BIGINT NOT NULL DEFAULT 0,
  reads BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(day, book_id)
);
CREATE SCHEMA catalog;
CREATE TABLE catalog.book (
  id UUID PRIMARY KEY, canonical_title TEXT NOT NULL,
  original_language TEXT, status TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.license (
  id UUID PRIMARY KEY, redistribution_allowed BOOLEAN,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.edition(
  id UUID PRIMARY KEY, license_id UUID,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.book_asset (
  id UUID PRIMARY KEY, book_id UUID NOT NULL, edition_id UUID,
  license_id UUID, status TEXT NOT NULL DEFAULT 'ACTIVE',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.book_asset_version (
  id UUID PRIMARY KEY, book_asset_id UUID NOT NULL, status TEXT NOT NULL,
  ingestion_item_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.rights_decision (
  action TEXT NOT NULL, distribution_status TEXT NOT NULL,
  territory TEXT NOT NULL, asset_id UUID, edition_id UUID,
  valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.ingestion_task (
  id UUID PRIMARY KEY, status TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL, lease_until TIMESTAMPTZ
);
CREATE TABLE catalog.source(
  id UUID PRIMARY KEY, code TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE catalog.source_record(id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE catalog.ingestion_job(
  id UUID PRIMARY KEY, source_id UUID NOT NULL, status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  items_discovered INTEGER NOT NULL, items_processed INTEGER NOT NULL,
  items_succeeded INTEGER NOT NULL, items_failed INTEGER NOT NULL
);
CREATE TABLE catalog.ingestion_item(
  id UUID PRIMARY KEY, ingestion_job_id UUID NOT NULL, source_id UUID NOT NULL,
  external_identifier TEXT NOT NULL, attempt_number INTEGER NOT NULL,
  source_record_id UUID, book_id UUID, edition_id UUID, status TEXT NOT NULL,
  started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, error_code TEXT,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
INSERT INTO catalog.ingestion_task(id, status, created_at, updated_at, lease_until) VALUES
  ('99999999-9999-4999-8999-999999999991', 'PENDING', now() - INTERVAL '20 minutes', now() - INTERVAL '20 minutes', NULL),
  ('99999999-9999-4999-8999-999999999992', 'RETRY_WAIT', now() - INTERVAL '30 minutes', now() - INTERVAL '2 minutes', NULL),
  ('99999999-9999-4999-8999-999999999993', 'RUNNING', now() - INTERVAL '10 minutes', now() - INTERVAL '10 minutes', now() - INTERVAL '1 minute'),
  ('99999999-9999-4999-8999-999999999994', 'FAILED', now() - INTERVAL '5 minutes', now(), NULL),
  ('99999999-9999-4999-8999-999999999995', 'SUCCEEDED', now() - INTERVAL '4 minutes', now(), NULL),
  ('99999999-9999-4999-8999-999999999996', 'CANCELLED', now() - INTERVAL '3 minutes', now(), NULL);
INSERT INTO catalog.book(id, canonical_title, original_language, status) VALUES
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'Fixture Reading', 'pt', 'ACTIVE'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', 'Unlicensed Fixture', 'en', 'ACTIVE');
INSERT INTO catalog.license(id, redistribution_allowed)
VALUES ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1', true);
INSERT INTO catalog.book_asset(id, book_id, license_id) VALUES
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'),
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', NULL);
INSERT INTO catalog.book_asset_version(id, book_asset_id, status) VALUES
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1', 'dddddddd-dddd-4ddd-8ddd-ddddddddddd1', 'AVAILABLE'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2', 'dddddddd-dddd-4ddd-8ddd-ddddddddddd2', 'AVAILABLE');
INSERT INTO catalog.source(id, code) VALUES ('11111111-1111-4111-8111-111111111111', 'GUTENBERG');
INSERT INTO catalog.ingestion_job(id, source_id, status, created_at, updated_at,
  items_discovered, items_processed, items_succeeded, items_failed)
VALUES ('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111',
  'COMPLETED_WITH_ERRORS', now() - INTERVAL '1 day', now(), 2, 2, 1, 1);
INSERT INTO catalog.ingestion_item(id, ingestion_job_id, source_id, external_identifier,
  attempt_number, book_id, status, started_at, finished_at, error_code, created_at, updated_at)
VALUES ('33333333-3333-4333-8333-333333333331', '22222222-2222-4222-8222-222222222222',
  '11111111-1111-4111-8111-111111111111', 'fixture:1', 1,
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'SUCCEEDED', now() - INTERVAL '1 day',
  now() - INTERVAL '1 day', NULL, now() - INTERVAL '1 day', now()),
  ('33333333-3333-4333-8333-333333333332', '22222222-2222-4222-8222-222222222222',
  '11111111-1111-4111-8111-111111111111', 'fixture:2', 2,
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', 'FAILED', now() - INTERVAL '1 day',
  now() - INTERVAL '1 day', 'ASSET_MISSING', now() - INTERVAL '1 day', now());
UPDATE catalog.book_asset_version SET ingestion_item_id =
  '33333333-3333-4333-8333-333333333331'
WHERE id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1';
CREATE SCHEMA analytics;
CREATE TABLE analytics.analysis_run(
  input_asset_version_id UUID NOT NULL, status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE analytics.analysis_job_item(
  id UUID PRIMARY KEY, status TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ
);
INSERT INTO analytics.analysis_job_item(id, status, created_at, started_at) VALUES
  ('44444444-4444-4444-8444-444444444441', 'PENDING', now() - INTERVAL '30 minutes', NULL),
  ('44444444-4444-4444-8444-444444444442', 'FAILED', now() - INTERVAL '20 minutes', now() - INTERVAL '20 minutes'),
  ('44444444-4444-4444-8444-444444444443', 'RUNNING', now() - INTERVAL '20 minutes', now() - INTERVAL '20 minutes');
CREATE TABLE analytics.excerpt(
  id UUID PRIMARY KEY, source_asset_version_id UUID NOT NULL,
  generation_method TEXT NOT NULL DEFAULT 'SENTENCE_WINDOW',
  body_eligible BOOLEAN NOT NULL, exclusion_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE analytics.excerpt_rank(
  excerpt_id UUID NOT NULL, ranker_code TEXT NOT NULL DEFAULT 'fixture-ranker',
  ranker_version TEXT NOT NULL DEFAULT 'v1', configuration_hash CHAR(64) NOT NULL DEFAULT repeat('a', 64),
  final_score DOUBLE PRECISION, exclusion_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO analytics.analysis_run(input_asset_version_id, status)
VALUES ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1', 'COMPLETED');
INSERT INTO analytics.excerpt(id, source_asset_version_id, body_eligible)
VALUES ('ffffffff-ffff-4fff-8fff-fffffffffff1', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1', true);
INSERT INTO analytics.excerpt_rank(excerpt_id, final_score)
VALUES ('ffffffff-ffff-4fff-8fff-fffffffffff1', 0.9);
CREATE SCHEMA reader_state;
CREATE TABLE reader_state.reading_day (
  subject_key TEXT NOT NULL, day DATE NOT NULL,
  minutes INTEGER NOT NULL DEFAULT 0, books_completed INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(subject_key, day)
);
CREATE TABLE reader_state.session (
  id UUID PRIMARY KEY, subject_key TEXT NOT NULL, book_id UUID,
  recommendation_item_id UUID, impression_id UUID,
  started_at TIMESTAMPTZ NOT NULL, ended_at TIMESTAMPTZ,
  active_seconds INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE reader_state.library (
  subject_key TEXT NOT NULL, book_id UUID NOT NULL, status TEXT NOT NULL DEFAULT 'SAVED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(subject_key, book_id)
);
CREATE SCHEMA publisher;
CREATE TABLE publisher.submission (
  id UUID PRIMARY KEY, subject_key TEXT NOT NULL, title TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE publisher.publication_review (
  id UUID PRIMARY KEY, submission_id UUID NOT NULL, status VARCHAR(32) NOT NULL,
  reviewer_subject_key TEXT NOT NULL, reason_code VARCHAR(80), notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE publisher.published_work (
  id UUID PRIMARY KEY, submission_id UUID NOT NULL UNIQUE, book_id UUID NOT NULL,
  edition_id UUID, publisher_subject_key TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  published_at TIMESTAMPTZ NOT NULL DEFAULT now(), withdrawn_at TIMESTAMPTZ,
  CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL))
);
CREATE TABLE publisher.rights_attestation (
  id UUID PRIMARY KEY, submission_id UUID NOT NULL, right_type VARCHAR(24) NOT NULL,
  territory VARCHAR(16) NOT NULL DEFAULT 'GLOBAL', evidence_reference TEXT,
  affirmed_by_subject TEXT NOT NULL, affirmed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE publisher.daily_publication_metric (
  day DATE NOT NULL, published_work_id UUID NOT NULL, impressions BIGINT NOT NULL DEFAULT 0,
  opens BIGINT NOT NULL DEFAULT 0, reads BIGINT NOT NULL DEFAULT 0,
  likes BIGINT NOT NULL DEFAULT 0, shares BIGINT NOT NULL DEFAULT 0,
  comments BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(day, published_work_id)
);
INSERT INTO reader_state.session(id, subject_key, book_id, started_at, ended_at, active_seconds) VALUES
  ('55555555-5555-4555-8555-555555555551', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', now() - INTERVAL '20 minutes', now() - INTERVAL '19 minutes', 60),
  ('55555555-5555-4555-8555-555555555552', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', now() - INTERVAL '15 minutes', now() - INTERVAL '13 minutes', 120),
  ('55555555-5555-4555-8555-555555555553', 'reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', now() - INTERVAL '10 minutes', now() - INTERVAL '5 minutes', 300),
  ('55555555-5555-4555-8555-555555555554', 'reader-2', NULL, now() - INTERVAL '25 hours', now() - INTERVAL '1 hour', 86401);
INSERT INTO reader_state.library(subject_key, book_id, status) VALUES
  ('library-state-reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'SAVED'),
  ('library-state-reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2', 'SAVED'),
  ('library-state-reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'SAVED');
INSERT INTO publisher.submission(id, subject_key, title, status, created_at, updated_at) VALUES
  ('66666666-6666-4666-8666-666666666661', 'publisher-1', 'Approved work', 'PUBLISHED', now() - INTERVAL '12 minutes', now() - INTERVAL '5 minutes'),
  ('66666666-6666-4666-8666-666666666662', 'publisher-2', 'Rejected work', 'REJECTED', now() - INTERVAL '10 minutes', now() - INTERVAL '9 minutes'),
  ('66666666-6666-4666-8666-666666666663', 'publisher-3', 'Changes requested', 'CHANGES_REQUESTED', now() - INTERVAL '8 minutes', now() - INTERVAL '6 minutes');
INSERT INTO publisher.publication_review(id, submission_id, status, reviewer_subject_key, created_at) VALUES
  ('66666666-6666-4666-8666-666666666671', '66666666-6666-4666-8666-666666666661', 'APPROVED', 'editor-1', now() - INTERVAL '5 minutes'),
  ('66666666-6666-4666-8666-666666666672', '66666666-6666-4666-8666-666666666662', 'REJECTED', 'editor-1', now() - INTERVAL '9 minutes'),
  ('66666666-6666-4666-8666-666666666673', '66666666-6666-4666-8666-666666666663', 'CHANGES_REQUESTED', 'editor-2', now() - INTERVAL '6 minutes');
INSERT INTO publisher.published_work(id, submission_id, book_id, publisher_subject_key, published_at)
VALUES ('66666666-6666-4666-8666-666666666681', '66666666-6666-4666-8666-666666666661',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'publisher-1', now() - INTERVAL '4 minutes');
INSERT INTO publisher.rights_attestation(id, submission_id, right_type, affirmed_by_subject, affirmed_at) VALUES
  ('66666666-6666-4666-8666-666666666691', '66666666-6666-4666-8666-666666666661', 'DISTRIBUTION', 'publisher-1', now() - INTERVAL '12 minutes'),
  ('66666666-6666-4666-8666-666666666692', '66666666-6666-4666-8666-666666666662', 'DISTRIBUTION', 'publisher-2', now() - INTERVAL '10 minutes');
INSERT INTO catalog.rights_decision(action, distribution_status, territory, valid_from, valid_until, created_at) VALUES
  ('DISTRIBUTION', 'APPROVED', 'GLOBAL', now() - INTERVAL '1 day', NULL, now() - INTERVAL '1 day'),
  ('PROCESSING', 'REVOKED', 'US', NULL, NULL, now() - INTERVAL '2 days'),
  ('DOWNLOAD', 'APPROVED', 'CA', now() - INTERVAL '3 days', now() - INTERVAL '1 day', now() - INTERVAL '3 days'),
  ('DISTRIBUTION', 'APPROVED', 'BR', now() + INTERVAL '1 day', NULL, now() - INTERVAL '4 days');
INSERT INTO behavior.events(id, occurred_at, identity_subject, book_id, event_type, payload) VALUES
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', now() - INTERVAL '6 minutes', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_PROGRESS', '{"percent":25}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2', now() - INTERVAL '5 minutes', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_PROGRESS', '{"percent":75}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3', now() - INTERVAL '4 minutes', 'reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_PROGRESS', '{"percent":100}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4', now() - INTERVAL '3 minutes', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_SAVE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa5', now() - INTERVAL '2 minutes', 'reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_SAVE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa6', now() - INTERVAL '1 minute', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_UNSAVE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa7', now() - INTERVAL '50 seconds', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_SESSION', '{"platform":"web","activeSeconds":120}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa8', now() - INTERVAL '40 seconds', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_SESSION', '{"platform":"mobile","activeSeconds":300}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa9', now() - INTERVAL '30 seconds', 'reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_SESSION', '{"platform":"mobile","activeSeconds":300}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa10', now() - INTERVAL '1 day 2 minutes', 'reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_SESSION', '{"activeSeconds":600}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa11', now() - INTERVAL '1 day 1 minute', 'reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_SESSION', '{"activeSeconds":1200}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa12', now() - INTERVAL '8 days', 'cohort-mature-reader', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_OPEN', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa13', now() - INTERVAL '1 day', 'cohort-mature-reader', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_OPEN', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa14', now() - INTERVAL '3 days', 'cohort-immature-reader', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_OPEN', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa15', now() - INTERVAL '20 seconds', 'social-reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_LIKE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa16', now() - INTERVAL '19 seconds', 'social-reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_LIKE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa17', now() - INTERVAL '18 seconds', 'social-reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_UNLIKE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa18', now() - INTERVAL '17 seconds', 'social-reader-1', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_SHARE', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa19', now() - INTERVAL '16 seconds', 'social-reader-2', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_COMMENT', '{}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa20', now() - INTERVAL '2 days', 'cohort-mature-reader', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_PROGRESS', '{"percent":25}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa21', now() - INTERVAL '1 day', 'cohort-mature-reader', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_PROGRESS', '{"percent":75}'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa22', now() - INTERVAL '20 seconds', 'cohort-mature-reader', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'READ_PROGRESS', '{"percent":100}');
UPDATE behavior.events SET identity_issuer = 'https://issuer-a.example/realm/bookrush'
WHERE identity_issuer IS NULL AND identity_subject IS NOT NULL;
INSERT INTO behavior.events(id, event_key, occurred_at, identity_issuer, identity_subject,
  book_id, event_type, payload)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa23', 'dashboard-gr01-issuer-collision', now() - INTERVAL '25 seconds',
  'https://issuer-b.example/realm/bookrush', 'reader-1',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 'BOOK_OPEN', '{"platform":"web"}');
INSERT INTO behavior.daily_book(day, book_id, impressions, opens, reads) VALUES
  (current_date - 1, 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 10, 5, 3),
  (current_date, 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', 20, 8, 4);
INSERT INTO reader_state.reading_day(subject_key, day, minutes, books_completed)
SELECT 'streak-reader-7d', day::date, 5, 0
FROM generate_series(current_date - 6, current_date, INTERVAL '1 day') AS day;
INSERT INTO reader_state.reading_day(subject_key, day, minutes, books_completed)
SELECT 'streak-reader-2d', day::date, 5, 0
FROM generate_series(current_date - 1, current_date, INTERVAL '1 day') AS day;
SQL
docker run -d --name "$service" --network "$network" --network-alias admin-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=bookrush \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/bookrush-platform \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-ingestion \
  -e KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token \
  -e KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush \
  -e KEYCLOAK_ADMIN_CLIENT_ID=bookrush-admin-service \
  -e KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" \
  -e BOOKRUSH_REPORTING_REFRESH_DELAY_MS=1000 "$image" >/dev/null
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
  remainder=$((${#payload} % 4))
  [ "$remainder" -eq 0 ] || [ "$remainder" -eq 2 ] || [ "$remainder" -eq 3 ]
  [ "$remainder" -eq 2 ] && payload="${payload}=="
  [ "$remainder" -eq 3 ] && payload="${payload}="
  payload="$(printf "%s" "$payload" | tr "_-" "/+" | base64 -d 2>/dev/null)"
  printf "%s" "$payload" | grep -q '"iss"'
  printf "%s" "$payload" | grep -q "http://keycloak:8080/realms/bookrush-platform"
  printf "%s" "$payload" | grep -q '"sub"'
  printf "%s" "$payload" | grep -q "bookrush-ingestion"
  printf "%s" "$payload" | grep -q "platform-admins"
'
unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/reading-progress-daily?days=7')"
[[ "$unauthenticated_status" == 401 ]]
platform_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/reader-day?days=7')"
[[ "$platform_unauthenticated_status" == 401 ]]
progress_json=''
for _ in $(seq 1 30); do
  progress_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/reading-progress-daily?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '.series[-1].reader_book_pairs_started // empty' <<<"$progress_json")" == 2 ]] && break
  sleep 1
done
if [[ "$(jq -r '.status // empty' <<<"$progress_json")" != UNTRUSTED ]]; then
  echo "Reading progress reporting response did not contain the expected aggregate." >&2
  docker exec "$postgres" psql -U bookrush -d bookrush -c \
    "SELECT projection_name, refresh_status, last_error_code, record_count FROM reporting.projection_health WHERE projection_name = 'reading_progress_daily'" >&2 || true
  docker logs --tail=160 "$service" >&2
  echo "$progress_json" >&2
  exit 1
fi
[[ "$(jq -r '[.series[-1].reader_book_pairs_started, .series[-1].reader_book_pairs_10, .series[-1].reader_book_pairs_50, .series[-1].reader_book_pairs_100, .series[-1].save_actions, .series[-1].unsave_actions, .series[-1].unique_savers, .series[-1].unique_unsavers] | @tsv' <<<"$progress_json")" == $'2\t2\t2\t1\t2\t1\t2\t1' ]]
[[ "$(jq -r '[.series[] | select(.reader_book_pairs_started == 1 and .reader_book_pairs_10 == 1 and .reader_book_pairs_50 == 1 and .reader_book_pairs_100 == 1)] | length' <<<"$progress_json")" == 1 ]]
echo 'Reading progress cohorts E2E: V36, 401/200, repeated progress, cross-day first passages and library actions OK'
habits_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
    "http://admin-service:8100/api/admin/v1/reporting/reading-habits-daily?days=7"
')"
[[ "$(jq -r '.status' <<<"$habits_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[-1].active_readers, .series[-1].reading_minutes, .series[-1].streak_1_day_users, .series[-1].streak_2_6_day_users, .series[-1].streak_7_29_day_users, .series[-1].streak_30_plus_day_users] | @tsv' <<<"$habits_json")" == $'2\t10\t0\t1\t1\t0' ]]
echo 'Reading habits reporting E2E: V25, streak buckets and privacy-safe aggregates OK'
reading_daily_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
    "http://admin-service:8100/api/admin/v1/reporting/reading-daily?days=7"
')"
if [[ "$(jq -r '[.series[] | select(.sessions == 3) | .sessions, .active_seconds, .p50_session_seconds, .p90_session_seconds] | @tsv' <<<"$reading_daily_json")" != $'3\t480\t120.0\t264.0' \
    || "$(jq -r '[.series[] | select(.sessions == 1) | .active_seconds, .p50_session_seconds, .p90_session_seconds] | @tsv' <<<"$reading_daily_json")" != $'86400\t86400.0\t86400.0' ]]; then
  echo "Reading daily response: $reading_daily_json" >&2
  docker exec "$postgres" psql -U bookrush -d bookrush -c \
    "SELECT projection_name, refresh_status, last_error_code, record_count FROM reporting.projection_health WHERE projection_name = 'reading_daily'" >&2 || true
  docker logs --tail=80 "$service" >&2
fi
[[ "$(jq -r '.status' <<<"$reading_daily_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.sessions == 3) | .sessions, .active_seconds, .p50_session_seconds, .p90_session_seconds] | @tsv' <<<"$reading_daily_json")" == $'3\t480\t120.0\t264.0' ]]
[[ "$(jq -r '[.series[] | select(.sessions == 1) | .active_seconds, .p50_session_seconds, .p90_session_seconds] | @tsv' <<<"$reading_daily_json")" == $'86400\t86400.0\t86400.0' ]]
[[ "$(jq -r '[.series[] | select(.sessions == 3) | .unique_readers] | @tsv' <<<"$reading_daily_json")" == $'4' ]]
[[ "$(jq -r '[.series[] | select(.sessions == 3) | .session_under_1m, .session_1m_5m, .session_5m_15m, .session_15m_plus] | @tsv' <<<"$reading_daily_json")" == $'0\t2\t1\t0' ]]
[[ "$(jq -r '[.series[] | select(.sessions == 1) | .session_under_1m, .session_1m_5m, .session_5m_15m, .session_15m_plus] | @tsv' <<<"$reading_daily_json")" == $'0\t0\t0\t1' ]]
echo 'Reading session reporting E2E: multi-reader sessions, 24h cap and p50/p90 OK'
reader_day_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
    "http://admin-service:8100/api/admin/v1/reporting/reader-day?days=7"
')"
[[ "$(jq -r '.status' <<<"$reader_day_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.platform == "web") | .active_users, .sessions, .active_seconds, .weekly_active_users] | @tsv' <<<"$reader_day_json")" == $'2\t1\t120\t2' ]]
[[ "$(jq -r '[.series[] | select(.platform == "mobile") | .active_users, .sessions, .active_seconds, .weekly_active_users] | @tsv' <<<"$reader_day_json")" == $'2\t2\t600\t2' ]]
echo 'Platform reporting E2E: authorized Web/Mobile split and platform-scoped WAU OK'
executive_summary_response="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  curl -sS -w "\\n%{http_code}" -H "Authorization: Bearer $ACCESS_TOKEN" \
    "http://admin-service:8100/api/admin/v1/reporting/executive-summary?days=7"
')"
executive_summary_status="${executive_summary_response##*$'\n'}"
executive_summary_json="${executive_summary_response%$'\n'*}"
if [[ "$executive_summary_status" != 200 ]]; then
  echo "Executive summary HTTP $executive_summary_status: $executive_summary_json" >&2
  docker logs --tail=160 "$service" 2>&1 \
    | rg '"level":"ERROR"' | cut -c1-3000 >&2 || true
  exit 1
fi
expected_unique_reader_peak="$(docker exec "$postgres" psql -At -U bookrush -d bookrush -c "
  SELECT max(daily_users) FROM (
    SELECT occurred_at::date,
      count(DISTINCT (identity_issuer, identity_subject)) AS daily_users
    FROM behavior.events
    WHERE identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
      AND event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS', 'BOOK_COMPLETE',
        'READ_COMPLETE', 'BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE',
        'BOOK_SHARE', 'BOOK_COMMENT')
      AND occurred_at >= current_date - 6 AND occurred_at < current_date + INTERVAL '1 day'
    GROUP BY occurred_at::date
  ) daily")"
[[ "$(jq -r '.current.daily_peak_users' <<<"$executive_summary_json")" == "$expected_unique_reader_peak" ]]
echo 'Executive summary E2E: overall daily user peak deduplicates identities across platforms/issuers'
trends_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/book-engagement-trends?days=7&minReaders=2')"
[[ "$trends_unauthenticated_status" == 401 ]]
trends_json=''
for _ in $(seq 1 30); do
  trends_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/book-engagement-trends?days=7&minReaders=2&top=5"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '.status // empty' <<<"$trends_json")" == UNTRUSTED \
      && "$(jq -r '[.series[] | select(.aggregation_level == "BOOK")] | length' <<<"$trends_json")" == 2 ]] && break
  sleep 1
done
if [[ "$(jq -r '.status // empty' <<<"$trends_json")" != UNTRUSTED ]]; then
  echo 'Book engagement trends projection did not become available.' >&2
  docker exec "$postgres" psql -U bookrush -d bookrush -c \
    "SELECT projection_name, refresh_status, last_error_code, record_count FROM reporting.projection_health WHERE projection_name = 'book_engagement_daily'" >&2 || true
  docker logs --tail=120 "$service" >&2
  echo "$trends_json" >&2
  exit 1
fi
[[ "$(jq -r '.status' <<<"$trends_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.aggregation_level == "BOOK")] | length' <<<"$trends_json")" == 2 ]]
[[ "$(jq -r '[.series[] | select(.aggregation_level == "BOOK") | .active_minutes] | @tsv' <<<"$trends_json")" == $'30\t12' ]]
[[ "$(jq -r '[.series[] | select(.aggregation_level == "LANGUAGE")] | length' <<<"$trends_json")" == 2 ]]
suppressed_trends_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
    "http://admin-service:8100/api/admin/v1/reporting/book-engagement-trends?days=7&minReaders=5&top=5"
')"
[[ "$(jq -r '.status' <<<"$suppressed_trends_json")" == NO_DATA ]]
[[ "$(jq -r '.population' <<<"$suppressed_trends_json")" == SUPPRESSED_BELOW_MIN_SAMPLE ]]
echo 'Book engagement trends E2E: 401/200, daily book/language series and minReaders suppression OK'
event_quality_json=''
for _ in $(seq 1 30); do
  event_quality_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/quality/events?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '.status // empty' <<<"$event_quality_json")" == UNTRUSTED \
      && "$(jq -r '[.series[].accepted_events] | add // 0' <<<"$event_quality_json")" -gt 0 ]] && break
  sleep 1
done
if [[ "$(jq -r '.status // empty' <<<"$event_quality_json")" != UNTRUSTED ]]; then
  echo 'Event quality projection did not become available.' >&2
  docker exec "$postgres" psql -U bookrush -d bookrush -c \
    "SELECT projection_name, refresh_status, last_error_code, record_count FROM reporting.projection_health WHERE projection_name = 'event_quality_daily'" >&2 || true
  docker logs --tail=120 "$service" >&2
  echo "$event_quality_json" >&2
  exit 1
fi
echo 'Event quality E2E: accepted-event daily projection and received_at grouping OK'
content_supply_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/content-supply?days=1')"
[[ "$content_supply_unauthenticated_status" == 401 ]]
content_supply_json=''
for _ in $(seq 1 30); do
  content_supply_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/content-supply?days=1"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '.series[-1].reader_ready_books // empty' <<<"$content_supply_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$content_supply_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[-1].active_books, .series[-1].reader_ready_books, .series[-1].excerpt_ready_books, .series[-1].available_asset_versions, .series[-1].eligible_excerpts, .series[-1].ranked_excerpts] | @tsv' <<<"$content_supply_json")" == $'2\t1\t1\t2\t1\t1' ]]
echo 'Content supply E2E: V26, 401/200, license-ready and excerpt-ready book counts OK'
coverage_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/content-coverage?days=1')"
[[ "$coverage_unauthenticated_status" == 401 ]]
coverage_json=''
for _ in $(seq 1 30); do
  coverage_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/content-coverage?days=1"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.language == "pt" and .rights_status == "RIGHTS_ALLOWED" and .reader_ready_books == 1)] | length' <<<"$coverage_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$coverage_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.language == "pt" and .rights_status == "RIGHTS_ALLOWED") | .active_books, .reader_ready_books, .eligible_excerpt_books, .ranked_books, .eligible_excerpts, .ranked_excerpts] | @tsv' <<<"$coverage_json")" == $'1\t1\t1\t1\t1\t1' ]]
[[ "$(jq -r '[.series[] | select(.language == "en" and .rights_status == "RIGHTS_NOT_CONFIRMED") | .active_books, .available_asset_books, .reader_ready_books] | @tsv' <<<"$coverage_json")" == $'1\t1\t0' ]]
! jq -e '.. | objects | has("book_id")' <<<"$coverage_json" >/dev/null
echo 'Content coverage E2E: V28, 401/200, language/rights/readiness aggregation and no book IDs OK'
rank_scores_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/content-rank-scores?days=1')"
[[ "$rank_scores_unauthenticated_status" == 401 ]]
rank_scores_json=''
for _ in $(seq 1 30); do
  rank_scores_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/content-rank-scores?days=1"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.ranker_code == "fixture-ranker" and .score_decile == 0 and .excerpt_count == 1)] | length' <<<"$rank_scores_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$rank_scores_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.ranker_code == "fixture-ranker") | .score_min, .score_max, .excerpt_count] | @tsv' <<<"$rank_scores_json")" == $'0.9\t0.9\t1' ]]
echo 'Content rank score E2E: V29, 401/200, config-scoped score deciles OK'
excerpt_production_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/excerpt-production?days=1')"
[[ "$excerpt_production_unauthenticated_status" == 401 ]]
excerpt_production_json=''
for _ in $(seq 1 30); do
  excerpt_production_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/excerpt-production?days=1"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.language == "pt" and .outcome == "RANKED" and .excerpt_count == 1)] | length' <<<"$excerpt_production_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$excerpt_production_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.language == "pt" and .outcome == "RANKED") | .rights_status, .reason_bucket] | @tsv' <<<"$excerpt_production_json")" == $'RIGHTS_ALLOWED\tNONE' ]]
! jq -e '.. | objects | has("excerpt_id") or has("book_id")' <<<"$excerpt_production_json" >/dev/null
echo 'Excerpt production E2E: V30, 401/200, ranked outcome, rights and aggregate-only response OK'
import_quality_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/import-quality-daily?days=7')"
[[ "$import_quality_unauthenticated_status" == 401 ]]
import_quality_json=''
for _ in $(seq 1 30); do
  import_quality_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/import-quality-daily?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.source_code == "GUTENBERG" and .item_status == "SUCCEEDED" and .asset_outcome == "AVAILABLE")] | length' <<<"$import_quality_json")" == 1 ]] && break
  sleep 1
done
if [[ "$(jq -r '.status // empty' <<<"$import_quality_json")" != UNTRUSTED ]]; then
  docker exec "$postgres" psql -U bookrush -d bookrush -c \
    "SELECT projection_name, refresh_status, last_error_code, record_count FROM reporting.projection_health WHERE projection_name = 'import_quality_daily'" >&2 || true
  docker logs --tail=120 "$service" >&2
  echo "$import_quality_json" >&2
  exit 1
fi
[[ "$(jq -r '.status' <<<"$import_quality_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.source_code == "GUTENBERG" and .item_status == "SUCCEEDED") | .item_count, .retry_attempt_count, .rights_outcome, .asset_outcome] | @tsv' <<<"$import_quality_json")" == $'1\t0\tALLOWED\tAVAILABLE' ]]
[[ "$(jq -r '[.series[] | select(.source_code == "GUTENBERG" and .item_status == "FAILED") | .error_bucket, .rights_outcome, .asset_outcome, .item_count, .retry_attempt_count] | @tsv' <<<"$import_quality_json")" == $'ASSET\tNOT_CONFIRMED\tNO_ASSET\t1\t1' ]]
echo 'Import quality E2E: V31, 401/200, retries/error/right/asset fixture OK'
backlog_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/backlog?days=7')"
[[ "$backlog_unauthenticated_status" == 401 ]]
backlog_json=''
for _ in $(seq 1 30); do
  backlog_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/backlog?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.source_type == "ANALYTICS" and .queue_name == "analytics.analysis_job_item" and .pending_count == 1)] | length' <<<"$backlog_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$backlog_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.source_type == "ANALYTICS" and .queue_name == "analytics.analysis_job_item") | .oldest_pending_age_seconds >= 900, .failed_count, .stuck_count] | @tsv' <<<"$backlog_json")" == $'true\t1\t1' ]]
[[ "$(jq -r '[.series[] | select(.source_type == "INGESTION" and .queue_name == "catalog.ingestion_task") | .pending_count, .failed_count, .stuck_count] | @tsv' <<<"$backlog_json")" == $'2\t1\t1' ]]
echo 'Backlog E2E: V32, 401/200, queue ages/failures/stuck and projection freshness rows OK'
funnel_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/editorial-submission-funnel-daily?days=7')"
[[ "$funnel_unauthenticated_status" == 401 ]]
editorial_funnel_json=''
for _ in $(seq 1 30); do
  editorial_funnel_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/editorial-submission-funnel-daily?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.submissions == 3 and .published_submissions == 1)] | length' <<<"$editorial_funnel_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$editorial_funnel_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.submissions == 3) | .reviewed_submissions, .approved_submissions, .published_submissions, .active_published_submissions, .rights_attested_submissions] | @tsv' <<<"$editorial_funnel_json")" == $'3\t1\t1\t1\t2' ]]
! jq -e '.series[] | has("subject_key") or has("submission_id") or has("title")' <<<"$editorial_funnel_json" >/dev/null
echo 'Editorial submission funnel E2E: V33, 401/200, cohort stages and aggregate-only response OK'
rights_status_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/rights-status-daily?days=7')"
[[ "$rights_status_unauthenticated_status" == 401 ]]
rights_status_json=''
for _ in $(seq 1 30); do
  rights_status_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/rights-status-daily?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.source_type == "CATALOG_DECISION" and .right_type == "DISTRIBUTION" and .status == "ACTIVE" and .item_count == 1)] | length' <<<"$rights_status_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$rights_status_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.source_type == "CATALOG_DECISION" and .status == "REVOKED") | .item_count] | add' <<<"$rights_status_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.source_type == "CATALOG_DECISION" and .status == "EXPIRED") | .item_count] | add' <<<"$rights_status_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.source_type == "CATALOG_DECISION" and .status == "NOT_YET_VALID") | .item_count] | add' <<<"$rights_status_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.source_type == "PUBLISHER_ATTESTATION" and .status == "ATTESTED") | .item_count] | add' <<<"$rights_status_json")" == 2 ]]
! jq -e '.series[] | has("evidence_reference") or has("affirmed_by_subject") or has("actor")' <<<"$rights_status_json" >/dev/null
echo 'Rights status E2E: V34, 401/200, active/revoked/expired decisions and distinct publisher attestations OK'
social_events_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/social-daily?days=7')"
[[ "$social_events_unauthenticated_status" == 401 ]]
social_events_json=''
for _ in $(seq 1 30); do
  social_events_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/social-daily?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.behavior_like_events == 2 and .behavior_unlike_events == 1)] | length' <<<"$social_events_json")" == 1 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$social_events_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[] | select(.behavior_like_events == 2) | .behavior_unlike_events, .behavior_save_events, .behavior_unsave_events, .behavior_share_events, .behavior_comment_events, .behavior_unique_actors, .behavior_distinct_books] | @tsv' <<<"$social_events_json")" == $'1\t2\t1\t1\t1\t4\t1' ]]
! jq -e '.series[] | has("identity_subject") or has("actor_subject_key") or has("book_id")' <<<"$social_events_json" >/dev/null
echo 'Social interaction E2E: V35, 401/200, like/unlike/save/unsave/share/comment events, distinct counts and aggregate-only response OK'
queue_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/ingestion-queue?days=7')"
[[ "$queue_unauthenticated_status" == 401 ]]
queue_json=''
for _ in $(seq 1 30); do
  queue_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/ingestion-queue?days=7"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '[.series[]? | select(.queue_status == "DONE" and .task_count == 1)] | length' <<<"$queue_json")" == 1 ]] && break
  sleep 1
done
if [[ "$(jq -r '.status // empty' <<<"$queue_json")" != UNTRUSTED ]]; then
  echo "Ingestion queue reporting response did not contain the expected projection." >&2
  docker exec "$postgres" psql -U bookrush -d bookrush -c \
    "SELECT projection_name, refresh_status, last_error_code, record_count FROM reporting.projection_health WHERE projection_name = 'ingestion_queue_snapshot'" >&2 || true
  docker logs --tail=160 "$service" >&2
  echo "$queue_json" >&2
  exit 1
fi
[[ "$(jq -r '[.series[] | select(.queue_status == "PENDING") | .task_count, .oldest_task_age_seconds >= 900] | @tsv' <<<"$queue_json")" == $'1\ttrue' ]]
[[ "$(jq -r '[.series[] | select(.queue_status == "RETRY_WAIT") | .task_count] | first' <<<"$queue_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.queue_status == "RUNNING") | .task_count, .expired_lease_count] | @tsv' <<<"$queue_json")" == $'1\t1' ]]
[[ "$(jq -r '[.series[] | select(.queue_status == "FAILED") | .task_count] | first' <<<"$queue_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.queue_status == "CANCELLED") | .task_count] | first' <<<"$queue_json")" == 1 ]]
! jq -e '.series[] | has("id") or has("job_id") or has("task_id")' <<<"$queue_json" >/dev/null
queue_metrics="$(docker exec "$probe" curl -fsS http://admin-service:8100/actuator/prometheus)"
if ! grep -Eq '^bookrush_ingestion_queue_tasks\{[^}]*state="PENDING"[^}]*\} 1(\.0)?$' <<<"$queue_metrics"; then
  echo 'Expected PL-02 Prometheus gauges were not found; exported queue metrics:' >&2
  grep -E 'bookrush_ingestion_queue' <<<"$queue_metrics" >&2 || true
  exit 1
fi
grep -Eq '^bookrush_ingestion_queue_oldest_task_age_seconds\{[^}]*state="PENDING"[^}]*\} [0-9]+(\.0)?$' <<<"$queue_metrics"
grep -Eq '^bookrush_ingestion_queue_expired_leases\{[^}]*state="RUNNING"[^}]*\} 1(\.0)?$' <<<"$queue_metrics"
echo 'Ingestion queue E2E: V27, 401/200, pending age, state counts and expired lease OK'
retention_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
    "http://admin-service:8100/api/admin/v1/reporting/retention-cohort?days=14"
')"
[[ "$(jq -r '[.series[] | select(.offset_day == 7 and .mature == true and .cohort_users == 1 and .retained_users == 1)] | length' <<<"$retention_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.offset_day == 7 and .mature == false and .cohort_users == 1)] | length' <<<"$retention_json")" == 1 ]]
[[ "$(jq -r '[.series[] | select(.offset_day == 0 and .cohort_users == 3 and .retained_users == 3)] | length' <<<"$retention_json")" == 1 ]]
echo 'Retention cohort E2E: D7 mature and immature cohort states remain distinct'
library_unauthenticated_status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  'http://admin-service:8100/api/admin/v1/reporting/library-state')"
[[ "$library_unauthenticated_status" == 401 ]]
library_state_json=''
for _ in $(seq 1 30); do
  library_state_json="$(docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
    curl -fsS -H "Authorization: Bearer $ACCESS_TOKEN" \
      "http://admin-service:8100/api/admin/v1/reporting/library-state"
  ' 2>/dev/null || true)"
  [[ "$(jq -r '.series[0].saved_book_pairs // empty' <<<"$library_state_json")" == 3 ]] && break
  sleep 1
done
[[ "$(jq -r '.status' <<<"$library_state_json")" == UNTRUSTED ]]
[[ "$(jq -r '[.series[0].saved_book_pairs, .series[0].saved_books, .series[0].saved_subject_keys] | @tsv' <<<"$library_state_json")" == $'3\t2\t2' ]]
[[ "$(jq -r '.unit' <<<"$library_state_json")" == current_snapshot ]]
echo 'Library state E2E: V38, 401/200, current aggregate 3 pairs/2 books/2 subject keys, no time series'
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
  # Confirm the same moderation state is enforced by the reader-facing service.
  expected=0
  [[ "$visibility" == VISIBLE ]] && expected=1
  docker exec "$probe" curl -fsS -H "Authorization: Bearer $token" \
    "http://$social_service:8096/api/v1/social/books/33333333-3333-4333-8333-333333333333/comments" \
    | jq -e --arg id "$legacy_comment_id" --argjson expected "$expected" \
      '[.[] | select(.id == $id)] | length == $expected' >/dev/null
done
# Pagination, literal search, visibility filters and immutable decision history.
docker exec "$probe" curl -fsS -G -H "Authorization: Bearer $token" \
  --data-urlencode 'search=Ana Leitora' --data-urlencode 'visibility=VISIBLE' \
  --data-urlencode 'size=1' --data-urlencode 'page=0' \
  http://admin-web/api/v1/admin/moderation/comments/page \
  | jq -e --arg id "$legacy_comment_id" '.totalItems == 1 and .last == true and .items[0].id == $id' >/dev/null
docker exec "$probe" curl -fsS -G -H "Authorization: Bearer $token" \
  --data-urlencode 'search=%' http://admin-web/api/v1/admin/moderation/comments/page \
  | jq -e '.totalItems == 0 and (.items | length) == 0' >/dev/null
docker exec "$probe" curl -fsS -G -H "Authorization: Bearer $token" \
  --data-urlencode 'page=1' --data-urlencode 'size=1' \
  http://admin-web/api/v1/admin/moderation/comments/page \
  | jq -e '.totalItems == 1 and .last == true and (.items | length) == 0' >/dev/null
history_url="http://admin-web/api/v1/admin/moderation/comments/$legacy_comment_id/history"
docker exec "$probe" curl -fsS -H "Authorization: Bearer $token" "$history_url" \
  | jq -e 'length == 3 and .[0].visibility == "VISIBLE" and .[0].previous_visibility == "REMOVED" and all(.[]; .reason == "E2E_REVIEW")' >/dev/null
# Invalid requests must leave the comment and audit unchanged.
for payload in \
  "{\"commentId\":\"$legacy_comment_id\",\"visibility\":\"HIDDEN\"}" \
  "{\"commentId\":\"$legacy_comment_id\",\"visibility\":\"INVALID\",\"reasonCode\":\"test\"}"; do
  status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data "$payload" \
    http://admin-web/api/v1/admin/moderation/comments)"
  [[ "$status" == 400 ]]
done
# Repeating the current state is idempotent and produces no extra audit entry.
docker exec "$probe" curl -fsS -X POST -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' \
  --data "{\"commentId\":\"$legacy_comment_id\",\"visibility\":\"VISIBLE\",\"reasonCode\":\"repeat review\"}" \
  http://admin-web/api/v1/admin/moderation/comments | jq -e '.updated == false' >/dev/null
docker exec "$probe" curl -fsS -H "Authorization: Bearer $token" "$history_url" \
  | jq -e 'length == 3' >/dev/null
# A failed audit insert must roll back visibility as well.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c "ALTER TABLE admin.audit_log ADD CONSTRAINT e2e_audit_failure CHECK (details->>'reason' IS DISTINCT FROM 'rollback probe')" >/dev/null
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  --data "{\"commentId\":\"$legacy_comment_id\",\"visibility\":\"HIDDEN\",\"reasonCode\":\"rollback probe\"}" \
  http://admin-web/api/v1/admin/moderation/comments)"
[[ "$status" == 500 ]]
docker exec "$postgres" psql -At -U bookrush -d bookrush \
  -c "SELECT visibility FROM social.comment_moderation_state WHERE comment_id='$legacy_comment_id'" | grep -qx VISIBLE
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c 'ALTER TABLE admin.audit_log DROP CONSTRAINT e2e_audit_failure' >/dev/null
for url in http://admin-web/api/v1/admin/moderation/comments/page "$history_url"; do
  status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' "$url")"
  [[ "$status" == 401 ]]
done
# A freshly issued platform token without operator privileges must be forbidden.
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh create users -r bookrush-platform \
  -s username=disposable-reviewer -s firstName=Disposable -s lastName=Reviewer \
  -s email=disposable-reviewer@bookrush.test -s enabled=true -s emailVerified=true \
  -s 'requiredActions=[]' >/dev/null
reviewer_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get users \
  -r bookrush-platform -q username=disposable-reviewer --fields id --format csv --noquotes)"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh set-password -r bookrush-platform \
  --userid "$reviewer_id" --new-password "$human_password" >/dev/null
reviewer_response="$(docker exec "$probe" curl -fsS -X POST \
  http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token \
  -d grant_type=password -d client_id=labsoft-admin-web \
  --data-urlencode username=disposable-reviewer --data-urlencode "password=$human_password")"
reviewer_token="$(jq -er '.access_token' <<<"$reviewer_response")"
for url in http://admin-web/api/v1/admin/moderation/comments/page "$history_url"; do
  status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $reviewer_token" "$url")"
  [[ "$status" == 403 ]]
  status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
    -H 'Authorization: Bearer invalid-token' "$url")"
  [[ "$status" == 401 ]]
done
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $reviewer_token" -H 'Content-Type: application/json' \
  --data "{\"commentId\":\"$legacy_comment_id\",\"visibility\":\"HIDDEN\",\"reasonCode\":\"forbidden review\"}" \
  http://admin-web/api/v1/admin/moderation/comments)"
[[ "$status" == 403 ]]
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $token" \
  http://admin-web/api/v1/admin/moderation/comments/99999999-9999-4999-8999-999999999999/history)"
[[ "$status" == 404 ]]
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X OPTIONS \
  -H 'Origin: https://bookrush.jteodoro.tec.br' -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization,content-type,x-admin-client' \
  http://admin-web/api/v1/admin/moderation/comments)"
[[ "$status" == 200 ]]
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

# Product-realm owner removal uses the same issuer/audience contract as Compose.
docker run -d --name "$product_social" --network "$network" \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=bookrush \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/bookrush \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=account "$social_image" >/dev/null
for _ in $(seq 1 90); do
  docker exec "$probe" curl -fsS "http://$product_social:8096/actuator/health" >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$probe" curl -fsS "http://$product_social:8096/actuator/health" >/dev/null
product_client_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients \
  -r bookrush -q clientId=bookrush-web --fields id --format csv --noquotes)"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh update "clients/$product_client_id" \
  -r bookrush -s directAccessGrantsEnabled=true >/dev/null
for reader_name in comment-owner comment-other; do
  docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh create users -r bookrush \
    -s username="$reader_name" -s firstName=Comment -s lastName=Reader \
    -s email="$reader_name@bookrush.test" -s enabled=true -s emailVerified=true -s 'requiredActions=[]' >/dev/null
  reader_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get users -r bookrush \
    -q username="$reader_name" --fields id --format csv --noquotes)"
  docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh set-password -r bookrush \
    --userid "$reader_id" --new-password "$human_password" >/dev/null
  reader_token="$(docker exec "$probe" curl -fsS -X POST \
    http://keycloak:8080/realms/bookrush/protocol/openid-connect/token \
    -d grant_type=password -d client_id=bookrush-web --data-urlencode "username=$reader_name" \
    --data-urlencode "password=$human_password" | jq -er '.access_token')"
  token_payload="${reader_token#*.}"
  token_payload="${token_payload%%.*}"
  case $((${#token_payload} % 4)) in 2) token_payload="${token_payload}==" ;; 3) token_payload="${token_payload}=" ;; esac
  printf '%s' "$token_payload" | tr '_-' '/+' | base64 -d \
    | jq -e '(.aud | if type == "array" then index("account") != null else . == "account" end) and .azp == "bookrush-web" and (.sub | type == "string" and length > 0)' >/dev/null
  if [[ "$reader_name" == comment-owner ]]; then owner_token="$reader_token"; owner_subject="$reader_id"; else other_token="$reader_token"; other_subject="$reader_id"; fi
done
product_base="http://$product_social:8096/api/v1/social"
comments_url="$product_base/books/33333333-3333-4333-8333-333333333333/comments"
echo 'Comment removal E2E: publishing with product token'
owner_comment_id="$(docker exec "$probe" curl -fsS -X POST -H "Authorization: Bearer $owner_token" \
  -H 'Content-Type: application/json' --data '{"body":"owner removal fixture"}' "$comments_url" | jq -er '.id')"
for reader_kind in owner other; do
  expected=false
  read_token="$other_token"
  if [[ "$reader_kind" == owner ]]; then expected=true; read_token="$owner_token"; fi
  docker exec "$probe" curl -fsS -H "Authorization: Bearer $read_token" "$comments_url" \
    | jq -e --arg id "$owner_comment_id" --argjson expected "$expected" \
      '.[] | select(.id == $id) | .canDelete == $expected' >/dev/null
done
echo 'Comment removal E2E: product publication and ownership flags OK'
delete_url="$product_base/comments/$owner_comment_id"
for identity in absent invalid other; do
  case "$identity" in
    absent) status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$delete_url")"; expected=401 ;;
    invalid) status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X DELETE -H 'Authorization: Bearer invalid-token' "$delete_url")"; expected=401 ;;
    other) status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $other_token" "$delete_url")"; expected=403 ;;
  esac
  [[ "$status" == "$expected" ]]
done
echo 'Comment removal E2E: invalid and foreign author requests rejected'
# A reported and hidden comment can still be removed by its owner, preserving both records.
docker exec "$probe" curl -fsS -X POST -H "Authorization: Bearer $other_token" \
  -H 'Content-Type: application/json' --data "{\"commentId\":\"$owner_comment_id\",\"reason\":\"review fixture\"}" \
  "$product_base/reports" >/dev/null
echo 'Comment removal E2E: product report recorded'
docker exec "$probe" curl -fsS -X POST -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' --data "{\"commentId\":\"$owner_comment_id\",\"visibility\":\"HIDDEN\",\"reasonCode\":\"preserve evidence\"}" \
  http://admin-web/api/v1/admin/moderation/comments >/dev/null
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $owner_token" "$delete_url")"
[[ "$status" == 204 ]]
removed_at="$(docker exec "$postgres" psql -At -U bookrush -d bookrush \
  -c "SELECT author_deleted_at::text FROM social.comments WHERE id='$owner_comment_id'")"
test -n "$removed_at"
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $owner_token" "$delete_url")"
[[ "$status" == 204 ]]
[[ "$removed_at" == "$(docker exec "$postgres" psql -At -U bookrush -d bookrush \
  -c "SELECT author_deleted_at::text FROM social.comments WHERE id='$owner_comment_id'")" ]]
docker exec "$probe" curl -fsS -H "Authorization: Bearer $owner_token" "$comments_url" \
  | jq -e --arg id "$owner_comment_id" 'all(.[]; .id != $id)' >/dev/null
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  --data "{\"commentId\":\"$owner_comment_id\",\"visibility\":\"VISIBLE\",\"reasonCode\":\"restore blocked\"}" \
  http://admin-web/api/v1/admin/moderation/comments)"
[[ "$status" == 409 ]]
docker exec "$probe" curl -fsS -G -H "Authorization: Bearer $token" \
  --data-urlencode "search=$owner_comment_id" --data-urlencode 'visibility=REMOVED' \
  http://admin-web/api/v1/admin/moderation/comments/page \
  | jq -e '.totalItems == 1 and .items[0].author_deleted_at != null' >/dev/null
docker exec "$postgres" psql -At -U bookrush -d bookrush \
  -c "SELECT count(*) FROM social.comments c JOIN social.comment_moderation_state m ON m.comment_id=c.id JOIN social.reports r ON r.comment_id=c.id WHERE c.id='$owner_comment_id' AND c.body='owner removal fixture' AND m.visibility='HIDDEN'" | grep -qx 1
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $owner_token" "$product_base/comments/99999999-9999-4999-8999-999999999999")"
[[ "$status" == 404 ]]
headers="$(docker exec "$probe" curl -sS -D - -o /dev/null -X OPTIONS \
  -H 'Origin: https://bookrush.jteodoro.tec.br' -H 'Access-Control-Request-Method: DELETE' \
  -H 'Access-Control-Request-Headers: authorization' "$delete_url")"
printf '%s' "$headers" | grep -iq 'Access-Control-Allow-Origin: https://bookrush.jteodoro.tec.br'
printf '%s' "$headers" | grep -iq 'Access-Control-Allow-Methods:.*DELETE'
echo 'Comment author removal E2E: product ownership, 401/403/404/204, idempotency, evidence, restore409, CORS and V8 upgrade OK'

# Incoming followers are counted from persisted relations, respecting profile privacy.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c "INSERT INTO reader_profile.profile(subject_key,display_name,is_public) VALUES ('$owner_subject','Follower target',true),('$other_subject','Follower viewer',true) ON CONFLICT DO NOTHING" >/dev/null
docker exec -i "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  < "$repo_dir/services/reader-profile-service/src/main/resources/db/migration/V3__profile_privacy.sql" >/dev/null
followers_url="$product_base/followers?user=$owner_subject"
follow_url="$product_base/follow?user=$owner_subject"
docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
  | jq -e '.visible == true and .followerCount == 0 and .following == false' >/dev/null
for _ in 1 2; do
  docker exec "$probe" curl -fsS -X PUT -H "Authorization: Bearer $other_token" "$follow_url" >/dev/null
  docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
    | jq -e '.visible == true and .followerCount == 1 and .following == true' >/dev/null
done
# A reader's following count must not be confused with their incoming followers.
docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$product_base/followers" \
  | jq -e '.visible == true and .followerCount == 0' >/dev/null
# PRIVATE hides numbers from others while retaining the owner's access.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c "UPDATE reader_profile.profile_privacy SET social_graph_visibility='PRIVATE' WHERE subject_key='$owner_subject'" >/dev/null
docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
  | jq -e '.visible == false and .followerCount == null' >/dev/null
docker exec "$probe" curl -fsS -H "Authorization: Bearer $owner_token" "$product_base/followers" \
  | jq -e '.visible == true and .followerCount == 1' >/dev/null
# FOLLOWERS is visible only while the viewer actually follows the target.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c "UPDATE reader_profile.profile_privacy SET social_graph_visibility='FOLLOWERS' WHERE subject_key='$owner_subject'" >/dev/null
docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
  | jq -e '.visible == true and .followerCount == 1' >/dev/null
for _ in 1 2; do
  docker exec "$probe" curl -fsS -X DELETE -H "Authorization: Bearer $other_token" "$follow_url" >/dev/null
  docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
    | jq -e '.visible == false and .followerCount == null and .following == false' >/dev/null
done
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c "UPDATE reader_profile.profile_privacy SET social_graph_visibility='PUBLIC' WHERE subject_key='$owner_subject'" >/dev/null
docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
  | jq -e '.visible == true and .followerCount == 0' >/dev/null
# Legacy private-profile flags also take precedence over a stale PUBLIC preference.
docker exec "$postgres" psql -v ON_ERROR_STOP=1 -U bookrush -d bookrush \
  -c "UPDATE reader_profile.profile SET is_public=false WHERE subject_key='$owner_subject'" >/dev/null
docker exec "$probe" curl -fsS -H "Authorization: Bearer $other_token" "$followers_url" \
  | jq -e '.visible == false and .followerCount == null' >/dev/null
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' "$followers_url")"
[[ "$status" == 401 ]]
status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $other_token" "$product_base/followers?user=missing-profile")"
[[ "$status" == 404 ]]
echo 'Follower count E2E: V9 upgrade, 0/1 counts, repeated follow/unfollow, incoming direction, privacy and authorization OK'

echo 'Admin moderation E2E: users, comment visibility/history/rollback, authorization, CORS and report lifecycle OK'
