#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TECHDOCS_BUCKET:?Set TECHDOCS_BUCKET}"
: "${TECHDOCS_S3_ENDPOINT:?Set TECHDOCS_S3_ENDPOINT}"
: "${AWS_ACCESS_KEY_ID:?Set publishing identity}"
: "${AWS_SECRET_ACCESS_KEY:?Set publishing identity}"
export PATH="$PWD/node_modules/.bin:$PATH"
while read -r source entity; do
  output=$(mktemp -d)
  techdocs-cli generate --no-docker --source-dir "$source" --output-dir "$output"
  techdocs-cli publish --publisher-type awsS3 --storage-name "$TECHDOCS_BUCKET" \
    --entity "$entity" --directory "$output" --awsEndpoint "$TECHDOCS_S3_ENDPOINT" --awsS3ForcePathStyle
  rm -r "$output"
done <<'SITES'
.. default/component/platform-handbook
. default/component/developer-portal
../frontend default/component/bookrush-frontend
../services/catalog-service default/component/catalog-service
../services/book-ingestion-service default/component/book-ingestion-service
SITES
