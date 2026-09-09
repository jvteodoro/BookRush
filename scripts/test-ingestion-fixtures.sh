#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
service_dir="$repo_root/services/book-ingestion-service"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker é obrigatório para o harness de ingestão; nenhum teste foi executado." >&2
  exit 2
fi
if ! docker info >/dev/null 2>&1; then
  echo "O daemon Docker não está acessível; nenhum teste foi executado." >&2
  exit 2
fi

cd "$service_dir"
./mvnw -q test
