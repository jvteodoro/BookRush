#!/usr/bin/env bash
set +x
set -euo pipefail
umask 077
directory="${JENKINS_HOME:?Jenkins home indisponível}/bookrush-demo"
mkdir -p -m 700 "$directory"
path="$directory/environment"
if [[ ! -f "$path" ]]; then
  if [[ -n "$(docker ps -aq --filter label=com.docker.compose.project=bookrush-demo)" ]]; then
    echo 'Demo existente sem ambiente persistido. Informe a credential usada na instalação.' >&2
    exit 2
  fi
  temporary="$(mktemp "$directory/environment.XXXXXX")"
  trap 'rm -f "$temporary"' EXIT
  printf 'DEMO_ADULT_USERNAME=demo-adult\nDEMO_MINOR_USERNAME=demo-minor\n' > "$temporary"
  keys=(POSTGRES_PASSWORD STORAGE_ACCESS_KEY STORAGE_SECRET_KEY ASSET_ADMIN_TOKEN
    KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD INGESTION_ADMIN_CLIENT_SECRET
    CATALOG_PRODUCT_ACCESS_CLIENT_SECRET BOOK_CONTENT_PRODUCT_ACCESS_CLIENT_SECRET
    READER_STATE_PRODUCT_ACCESS_CLIENT_SECRET READER_PROFILE_PRODUCT_ACCESS_CLIENT_SECRET
    SOCIAL_PRODUCT_ACCESS_CLIENT_SECRET BEHAVIOR_PRODUCT_ACCESS_CLIENT_SECRET BEHAVIOR_OPERATIONS_CLIENT_SECRET
    READER_BFF_SESSION_CLIENT_SECRET READER_BFF_SUPERVISION_CLIENT_SECRET
    SUBSCRIPTION_FAMILY_CLIENT_SECRET SUBSCRIPTION_BILLING_CLIENT_SECRET FAMILY_IDENTITY_CLIENT_SECRET
    FAMILY_SUPERVISION_ADMIN_SECRET FAMILY_MEMBERSHIP_CLIENT_SECRET DEMO_ADULT_PASSWORD DEMO_MINOR_PASSWORD)
  for key in "${keys[@]}"; do
    value="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
    printf '%s=%s\n' "$key" "$value" >> "$temporary"
  done
  mv "$temporary" "$path"
fi
printf '%s\n' "$path"
