#!/usr/bin/env bash
set -Eeuo pipefail

# Copy only the Grafana HTTP bootstrap vhost. Certbot is run separately.
if (( EUID != 0 )); then
    echo "Execute como root: sudo bash $0" >&2
    exit 1
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source_file="$script_dir/conf.d/bookrush-grafana.conf"
target_dir="${NGINX_BOOKRUSH_MODULE_DIR:-/etc/nginx/conf.d}"
target_file="$target_dir/bookrush-grafana.conf"

[[ -f "$source_file" ]] || { echo "Vhost ausente: $source_file" >&2; exit 1; }
[[ -d "$target_dir" ]] || { echo "Diretório Nginx ausente: $target_dir" >&2; exit 1; }
command -v install >/dev/null || { echo 'Comando install ausente.' >&2; exit 1; }

install -m 0644 "$source_file" "$target_file"
echo "Vhost Grafana copiado para $target_file"
