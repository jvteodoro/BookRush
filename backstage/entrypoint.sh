#!/bin/sh
set -eu

set -- node dist/index.cjs.js \
  --config ../../app-config.yaml \
  --config ../../app-config.docker.yaml

if [ "${BACKSTAGE_OIDC_ENABLED:-false}" = "true" ]; then
  : "${AUTH_OIDC_METADATA_URL:?AUTH_OIDC_METADATA_URL is required when BACKSTAGE_OIDC_ENABLED=true}"
  : "${AUTH_OIDC_CLIENT_ID:?AUTH_OIDC_CLIENT_ID is required when BACKSTAGE_OIDC_ENABLED=true}"
  : "${AUTH_OIDC_CLIENT_SECRET:?AUTH_OIDC_CLIENT_SECRET is required when BACKSTAGE_OIDC_ENABLED=true}"
  : "${AUTH_SESSION_SECRET:?AUTH_SESSION_SECRET is required when BACKSTAGE_OIDC_ENABLED=true}"
  set -- "$@" --config ../../app-config.oidc.yaml
fi

exec "$@"
