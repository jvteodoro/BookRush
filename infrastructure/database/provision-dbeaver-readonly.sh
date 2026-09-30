#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${BOOKRUSH_ENV_FILE:-$root_dir/.env}"

if [[ ! -f "$env_file" ]]; then
  echo "Arquivo .env ausente: $env_file" >&2
  exit 2
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

: "${POSTGRES_DB:?POSTGRES_DB deve estar definido no .env}"
: "${POSTGRES_USER:?POSTGRES_USER deve estar definido no .env}"
: "${DBEAVER_READONLY_USER:?DBEAVER_READONLY_USER deve estar definido no .env}"
: "${DBEAVER_READONLY_PASSWORD:?DBEAVER_READONLY_PASSWORD deve estar definido no .env}"

if [[ ! "$DBEAVER_READONLY_USER" =~ ^[a-z_][a-z0-9_]{0,62}$ ]]; then
  echo "DBEAVER_READONLY_USER deve ser um identificador PostgreSQL simples." >&2
  exit 2
fi

export PGPASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD deve estar definido no .env}"
trap 'unset PGPASSWORD DBEAVER_READONLY_PASSWORD' EXIT

compose=(docker compose --env-file "$env_file" -f "$root_dir/infrastructure/compose.yaml")

"${compose[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 \
  -v db_name="$POSTGRES_DB" \
  -v readonly_user="$DBEAVER_READONLY_USER" \
  -v readonly_password="$DBEAVER_READONLY_PASSWORD" \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L',
              :'readonly_user', :'readonly_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'readonly_user')
\gexec

SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD %L',
              :'readonly_user', :'readonly_password')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'db_name', :'readonly_user')
\gexec

SELECT current_user AS owner_role \gset

SELECT format('GRANT USAGE ON SCHEMA %I TO %I', nspname, :'readonly_user')
FROM pg_namespace
WHERE nspname IN ('catalog', 'ingestion', 'analytics')
\gexec

SELECT format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO %I', nspname, :'readonly_user')
FROM pg_namespace
WHERE nspname IN ('catalog', 'ingestion', 'analytics')
\gexec

SELECT format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I', nspname, :'readonly_user')
FROM pg_namespace
WHERE nspname IN ('catalog', 'ingestion', 'analytics')
\gexec

SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I GRANT SELECT ON TABLES TO %I',
              :'owner_role', nspname, :'readonly_user')
FROM pg_namespace
WHERE nspname IN ('catalog', 'ingestion', 'analytics')
\gexec

SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I GRANT USAGE, SELECT ON SEQUENCES TO %I',
              :'owner_role', nspname, :'readonly_user')
FROM pg_namespace
WHERE nspname IN ('catalog', 'ingestion', 'analytics')
\gexec
SQL

echo "Conta somente leitura para DBeaver reconciliada sem expor a senha."
