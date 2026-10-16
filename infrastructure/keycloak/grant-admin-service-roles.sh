#!/usr/bin/env bash
set -Eeuo pipefail

: "${KEYCLOAK_ADMIN_TOKEN_URI:?KEYCLOAK_ADMIN_TOKEN_URI ausente}"
: "${KEYCLOAK_ADMIN_BASE:?KEYCLOAK_ADMIN_BASE ausente}"
: "${KEYCLOAK_ADMIN_CLIENT_ID:?KEYCLOAK_ADMIN_CLIENT_ID ausente}"
: "${KEYCLOAK_ADMIN_CLIENT_SECRET:?KEYCLOAK_ADMIN_CLIENT_SECRET ausente}"
: "${KEYCLOAK_CONFIG_CLI_ADMIN_USER:?KEYCLOAK_CONFIG_CLI_ADMIN_USER ausente}"
: "${KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD:?KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD ausente}"

realm_base="${KEYCLOAK_ADMIN_BASE%/}"
server_base="${KEYCLOAK_ADMIN_TOKEN_URI%%/realms/*}"
token="$(curl -fsS -X POST "$server_base/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "username=${KEYCLOAK_CONFIG_CLI_ADMIN_USER}" \
  --data-urlencode "password=${KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD}" \
  --data-urlencode grant_type=password --data-urlencode client_id=admin-cli | jq -r '.access_token')"
[[ -n "$token" && "$token" != null ]] || { echo 'Token administrativo não foi emitido.' >&2; exit 2; }
auth=(-H "Authorization: Bearer $token")
client_id="$(curl -fsS "${realm_base}/clients?clientId=${KEYCLOAK_ADMIN_CLIENT_ID}" "${auth[@]}" | jq -r '.[0].id')"
service_user="$(curl -fsS "${realm_base}/clients/${client_id}/service-account-user" "${auth[@]}" | jq -r '.id')"
realm_management="$(curl -fsS "${server_base}/admin/realms/bookrush-platform/clients?clientId=realm-management" "${auth[@]}" | jq -r '.[0].id')"
roles="$(curl -fsS "${server_base}/admin/realms/bookrush-platform/clients/${realm_management}/roles?briefRepresentation=true" "${auth[@]}" | jq '[.[] | select(.name == "query-users" or .name == "manage-users")]')"
curl -fsS -X POST "${realm_base}/users/${service_user}/role-mappings/clients/${realm_management}" \
  "${auth[@]}" -H 'Content-Type: application/json' -d "$roles" >/dev/null
echo 'Roles do serviço admin reconciliadas.'
