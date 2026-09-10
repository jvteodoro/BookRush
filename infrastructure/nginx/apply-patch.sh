#!/usr/bin/env bash
set -Eeuo pipefail

echo "O patch monolítico foi substituído pelos módulos conf.d BookRush." >&2
exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/install-bookrush-vhosts.sh" "$@"
