#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
test -d .githooks || { echo '.githooks ausente' >&2; exit 1; }
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit .githooks/pre-push scripts/*.sh
echo "Git hooks instalados em $repo_dir/.githooks"
