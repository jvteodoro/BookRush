#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable proof that the checked-in realms can be rendered with runtime
# secrets and reconciled by keycloak-config-cli. It never uses the project
# Compose network, volumes, or .env file.
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-keycloak-reconcile.XXXXXX")"
network="bookrush-keycloak-reconcile-$RANDOM"
keycloak="bookrush-keycloak-reconcile-$RANDOM"
renderer="bookrush-keycloak-render-$RANDOM"
cli="bookrush-keycloak-cli-$RANDOM"
cli_image="adorsys/keycloak-config-cli:6.3.0-18.0.2"
grafana="bookrush-grafana-$RANDOM"
admin_password='disposable-admin-password'
cleanup() {
  rc=$?
  if ((rc != 0)) && docker container inspect "$keycloak" >/dev/null 2>&1; then
    echo '--- Keycloak disposable logs (failure) ---' >&2
    docker logs "$keycloak" >&2 || true
  fi
  docker rm -f "$cli" >/dev/null 2>&1 || true
  docker rm -f "$grafana" >/dev/null 2>&1 || true
  docker rm -f "$renderer" >/dev/null 2>&1 || true
  docker rm -f "$keycloak" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
}
trap cleanup EXIT

mkdir -m 700 "$work_dir/rendered"
docker create --name "$renderer" \
  -e JENKINS_OIDC_CLIENT_SECRET='disposable-jenkins-secret' \
  -e BACKSTAGE_OIDC_CLIENT_SECRET='disposable-backstage-secret' \
  -e INGESTION_ADMIN_CLIENT_SECRET='disposable-ingestion-secret' \
  -e INGESTION_CANONICAL_CLIENT_SECRET='disposable-canonical-secret' \
  -e INGESTION_ASSET_CLIENT_SECRET='disposable-asset-secret' \
  -e KEYCLOAK_AGE_PROFILE_ENABLED=true \
  -e KEYCLOAK_READER_STATE_CLIENT_ENABLED=true \
  -e KEYCLOAK_CONTENT_DISTRIBUTION_CLIENTS_ENABLED=true \
  -e KEYCLOAK_COMMUNITY_CLIENTS_ENABLED=true \
  -e KEYCLOAK_READING_SUPERVISION_ENABLED=true \
  -e KEYCLOAK_SESSION_BOOTSTRAP_ENABLED=true \
  -e SUBSCRIPTION_FAMILY_CLIENT_SECRET='disposable-subscription-family-secret' \
  -e SUBSCRIPTION_BILLING_CLIENT_SECRET='disposable-subscription-billing-secret' \
  -e CATALOG_PRODUCT_ACCESS_CLIENT_SECRET='disposable-catalog-access-secret' \
  -e BOOK_CONTENT_PRODUCT_ACCESS_CLIENT_SECRET='disposable-content-access-secret' \
  -e SOCIAL_PRODUCT_ACCESS_CLIENT_SECRET='disposable-social-access-secret' \
  -e READER_PROFILE_PRODUCT_ACCESS_CLIENT_SECRET='disposable-profile-access-secret' \
  -e BEHAVIOR_PRODUCT_ACCESS_CLIENT_SECRET='disposable-behavior-access-secret' \
  -e BEHAVIOR_OPERATIONS_CLIENT_SECRET='disposable-behavior-operations-secret' \
  -e READER_STATE_PRODUCT_ACCESS_CLIENT_SECRET='disposable-reader-state-secret' \
  -e READER_BFF_SESSION_CLIENT_SECRET='disposable-session-secret' \
  -e READER_BFF_SUPERVISION_CLIENT_SECRET='disposable-supervision-secret' \
  -e FAMILY_MEMBERSHIP_CLIENT_SECRET='disposable-membership-secret' \
  -e FAMILY_SUPERVISION_ADMIN_SECRET='disposable-supervision-admin-secret' \
  -e FAMILY_IDENTITY_CLIENT_SECRET='disposable-family-age-secret' \
  -e GRAFANA_OIDC_CLIENT_SECRET='disposable-grafana-secret' \
  -e GRAFANA_OIDC_REDIRECT_URI='http://grafana:3000/login/generic_oauth' \
  -e GRAFANA_OIDC_WEB_ORIGIN='http://grafana:3000' \
  --entrypoint python python:3.12-alpine \
  /tmp/render-config.py /tmp/source /tmp/rendered >/dev/null
docker cp "$repo_dir/infrastructure/keycloak/." "$renderer:/tmp/source"
docker cp "$repo_dir/infrastructure/keycloak/render-config.py" "$renderer:/tmp/render-config.py"
docker start -a "$renderer" >/dev/null
docker cp "$renderer:/tmp/rendered/." "$work_dir/rendered"
python3 - "$work_dir/rendered/bookrush-realm.json" "$work_dir/rendered/bookrush-platform-realm.json" <<'PY'
import json, sys
product, platform = (json.load(open(path, encoding="utf-8")) for path in sys.argv[1:])
clients = {item["clientId"]: item for item in platform["clients"]}
for client in ("bookrush-web", "bookrush-mobile"):
    scopes = set(next(item for item in product["clients"] if item["clientId"] == client)["defaultClientScopes"])
    assert {"profile", "email", "bookrush.catalog.read", "family.manage", "family.invites", "supervision.request", "subscription.read"} <= scopes
expected = {
    "bookrush-family-s2s", "bookrush-billing-s2s",
    "bookrush-reader-state-s2s", "bookrush-catalog-product-access-s2s",
    "bookrush-book-content-product-access-s2s", "bookrush-social-product-access-s2s",
    "bookrush-reader-profile-product-access-s2s", "bookrush-behavior-product-access-s2s",
    "bookrush-behavior-operations-s2s", "bookrush-reader-bff-session-s2s",
    "bookrush-reader-bff-supervision-s2s",
}
assert expected <= clients.keys()
product_clients = {item["clientId"]: item for item in product["clients"]}
birth = next(item for item in product["userProfile"]["attributes"] if item["name"] == "birthdate")
assert birth["permissions"]["edit"] == ["admin", "user"] and "pattern" in birth["validations"]
assert "bookrush-family-age-s2s" in product_clients
assert all("${" not in client.get("secret", "") for client in clients.values())
assert "defaultClientScopes" not in product_clients["bookrush-family-age-s2s"]
age_account = next(item for item in product["users"]
                   if item["username"] == "service-account-bookrush-family-age-s2s")
assert "view-users" in age_account["clientRoles"]["realm-management"]
print("Keycloak render: subscription, age, product workloads and user scopes OK")
PY

docker network create "$network" >/dev/null
docker create --name "$keycloak" --network "$network" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /opt/keycloak/bookrush-*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm' >/dev/null
python3 - "$work_dir/rendered/bookrush-realm.json" "$work_dir/keycloak-import-realm.json" <<'PY'
import json, sys
realm = json.load(open(sys.argv[1], encoding="utf-8"))
# keycloak-config-cli applies userProfile after boot; Keycloak's --import-realm rejects it.
realm.pop("userProfile", None)
json.dump(realm, open(sys.argv[2], "w", encoding="utf-8"))
PY
docker cp "$work_dir/keycloak-import-realm.json" "$keycloak:/opt/keycloak/bookrush-realm.json"
docker cp "$work_dir/rendered/bookrush-platform-realm.json" "$keycloak:/opt/keycloak/bookrush-platform-realm.json"
docker start "$keycloak" >/dev/null
probe="$(docker run -d --network "container:$keycloak" --entrypoint /bin/sh \
  curlimages/curl:8.10.1 -c 'sleep 180')"
cleanup_probe() { docker rm -f "$probe" >/dev/null 2>&1 || true; }
trap 'cleanup_probe; cleanup' EXIT
for _ in $(seq 1 90); do
  if docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration' >/dev/null 2>&1 \
      && docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush-platform/.well-known/openid-configuration' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration' >/dev/null

docker create --name "$cli" --network "$network" \
  --user "$(id -u):$(id -g)" \
  -e KEYCLOAK_URL=http://"$keycloak":8080 \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD="$admin_password" \
  -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
  -e IMPORT_FILES_LOCATIONS=/tmp/config/*.json \
  -e IMPORT_VARSUBSTITUTION_ENABLED=true \
  --entrypoint /bin/sh "$cli_image" \
  -c 'mkdir -p /tmp/config && cp /tmp/bookrush-*.json /tmp/config/ && exec java $JAVA_OPTS -jar /app/keycloak-config-cli.jar' >/dev/null
docker cp "$work_dir/rendered/bookrush-realm.json" "$cli:/tmp/bookrush-realm.json"
docker cp "$work_dir/rendered/bookrush-platform-realm.json" "$cli:/tmp/bookrush-platform-realm.json"
docker start -a "$cli"

docker create --name "$grafana" --network "$network" --network-alias grafana \
  -e GF_SECURITY_ADMIN_USER=admin \
  -e GF_SECURITY_ADMIN_PASSWORD=disposable-grafana-admin \
  -e GF_AUTH_ANONYMOUS_ENABLED=false \
  -e GF_USERS_ALLOW_SIGN_UP=false \
  -e GF_AUTH_GENERIC_OAUTH_ENABLED=true \
  -e GF_AUTH_GENERIC_OAUTH_ALLOW_ASSIGN_GRAFANA_ADMIN=true \
  -e GF_AUTH_GENERIC_OAUTH_NAME=Keycloak \
  -e GF_AUTH_GENERIC_OAUTH_CLIENT_ID=bookrush-grafana \
  -e GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET=disposable-grafana-secret \
  -e GF_AUTH_GENERIC_OAUTH_SCOPES='openid profile email' \
  -e 'GF_AUTH_GENERIC_OAUTH_EMAIL_ATTRIBUTE_PATH=email || preferred_username' \
  -e GF_AUTH_GENERIC_OAUTH_AUTH_URL="http://${keycloak}:8080/realms/bookrush-platform/protocol/openid-connect/auth" \
  -e GF_AUTH_GENERIC_OAUTH_TOKEN_URL="http://${keycloak}:8080/realms/bookrush-platform/protocol/openid-connect/token" \
  -e GF_AUTH_GENERIC_OAUTH_API_URL="http://${keycloak}:8080/realms/bookrush-platform/protocol/openid-connect/userinfo" \
  -e GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH="contains(groups[*], 'platform-admins') && 'Admin' || (contains(groups[*], 'platform-operators') || contains(groups[*], 'operators')) && 'Editor' || 'Viewer'" \
  -e GF_SERVER_ROOT_URL=http://grafana:3000 \
  grafana/grafana:11.3.0 >/dev/null
docker start "$grafana" >/dev/null
for _ in $(seq 1 90); do
  if docker exec "$grafana" wget -qO- http://127.0.0.1:3000/api/health >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$grafana" wget -qO- http://127.0.0.1:3000/api/health >/dev/null
oauth_location="$(docker run --rm --network "$network" curlimages/curl:8.10.1 \
  -fsS -D - -o /dev/null http://grafana:3000/login/generic_oauth \
  | awk -F': ' 'tolower($1) == "location" { print $2 }' | tr -d '\r')"
grep -q 'client_id=bookrush-grafana' <<<"$oauth_location"
grep -q 'redirect_uri=http%3A%2F%2Fgrafana%3A3000%2Flogin%2Fgeneric_oauth' <<<"$oauth_location"

docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/bookrush-platform/.well-known/openid-configuration' >/dev/null
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://127.0.0.1:8080 --realm master --user admin --password "$admin_password" >/dev/null
service_account_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get users \
  -r bookrush-platform -q username=service-account-bookrush-ingestion-admin \
  --fields id --format csv --noquotes | awk 'NR == 1 { first = $0 } END { print first }')"
test -n "$service_account_id"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get \
  "users/$service_account_id/role-mappings/realm" -r bookrush-platform \
  --fields name --format csv --noquotes | grep -x 'platform-operator' >/dev/null
age_client_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients \
  -r bookrush -q clientId=bookrush-family-age-s2s --fields id --format csv --noquotes \
  | awk 'NR == 1 { print; exit }')"
test -n "$age_client_id"
age_client="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get \
  "clients/$age_client_id" -r bookrush --format json)"
grep -q '"serviceAccountsEnabled" : true' <<<"$age_client"
age_service_account_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get users \
  -r bookrush -q username=service-account-bookrush-family-age-s2s \
  --fields id --format csv --noquotes | awk 'NR == 1 { print; exit }')"
test -n "$age_service_account_id"
realm_management_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients \
  -r bookrush -q clientId=realm-management --fields id --format csv --noquotes \
  | awk 'NR == 1 { print; exit }')"
test -n "$realm_management_id"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get \
  "users/$age_service_account_id/role-mappings/clients/$realm_management_id" -r bookrush \
  --fields name --format csv --noquotes | grep -x 'view-users' >/dev/null
grafana_client_id="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients \
  -r bookrush-platform -q clientId=bookrush-grafana \
  --fields id --format csv --noquotes | awk 'NR == 1 { print; exit }')"
test -n "$grafana_client_id"
grafana_client="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get \
  "clients/$grafana_client_id" -r bookrush-platform --format json)"
grep -q 'http://grafana:3000/login/generic_oauth' <<<"$grafana_client"
grep -q '"publicClient" : false' <<<"$grafana_client"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get \
  "clients/$grafana_client_id/protocol-mappers/models" -r bookrush-platform \
  --fields name --format csv --noquotes | grep -x 'grafana-groups' >/dev/null
for mapper in grafana-preferred-username grafana-email grafana-email-verified; do
  docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get \
    "clients/$grafana_client_id/protocol-mappers/models" -r bookrush-platform \
    --fields name --format csv --noquotes | grep -x "$mapper" >/dev/null
done
# keycloak-config-cli applies userProfile only when userProfileEnabled is declared; prove it was applied.
user_profile="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get users/profile -r bookrush)"
grep -q '"name" : "birthdate"' <<<"$user_profile"
echo 'Keycloak IaC + Grafana OAuth redirect: OK (ambiente descartável)'
