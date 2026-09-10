#!/usr/bin/env bash
set -Eeuo pipefail
echo "Os virtual hosts BookRush agora são instalados em conjunto." >&2
exec "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/install-bookrush-vhosts.sh" "$@"
