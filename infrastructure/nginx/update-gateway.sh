#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Atualiza somente o upstream do módulo gateway BookRush.
# Para instalar ou migrar todos os hosts, use install-bookrush-vhosts.sh.

if (( EUID != 0 )); then
    echo "Execute como root: sudo bash $0" >&2
    exit 1
fi
for command in nginx python3 flock mktemp cp mv date; do
    command -v "$command" >/dev/null || { echo "Comando ausente: $command" >&2; exit 1; }
done

module="${NGINX_BOOKRUSH_GATEWAY_MODULE:-/etc/nginx/conf.d/bookrush-gateway.conf}"
upstream="${BOOKRUSH_GATEWAY_UPSTREAM:-http://127.0.0.1:18081}"
lock_file="${NGINX_LOCK:-/run/lock/bookrush-nginx.lock}"
[[ -f "$module" ]] || { echo "Módulo não encontrado: $module" >&2; exit 1; }

mkdir -p "$(dirname "$lock_file")"
exec 9>"$lock_file"
flock -n 9 || { echo 'Outra atualização do Nginx está em andamento.' >&2; exit 1; }

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup="${module}.backup.${timestamp}"
candidate="$(mktemp "${module}.candidate.XXXXXX")"
trap 'rm -f "$candidate"' EXIT
cp -p -- "$module" "$backup"

MODULE="$module" CANDIDATE="$candidate" UPSTREAM="$upstream" python3 - <<'PY'
import os
import re
from pathlib import Path

module = Path(os.environ["MODULE"])
text = module.read_text()
upstream = os.environ["UPSTREAM"]
updated, count = re.subn(r"proxy_pass\s+http://127\.0\.0\.1:\d+\s*;", f"proxy_pass {upstream};", text)
if count != 1:
    raise SystemExit(f"esperado um proxy_pass local; encontrados {count}")
Path(os.environ["CANDIDATE"]).write_text(updated)
PY

if ! nginx -t -c "$candidate"; then
    echo "Módulo inválido; backup preservado em $backup" >&2
    exit 1
fi
mv -f -- "$candidate" "$module"
nginx -t
nginx -s reload
echo "Gateway configurado em $upstream. Backup: $backup"
