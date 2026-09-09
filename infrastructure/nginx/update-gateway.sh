#!/usr/bin/env bash
set -Eeuo pipefail

# Atualiza somente o virtual host BookRush para apontar ao gateway Docker.
# Execute no host com sudo: sudo bash infrastructure/nginx/update-gateway.sh

config="${NGINX_CONFIG:-/etc/nginx/nginx.conf}"
domain="${BOOKRUSH_DOMAIN:-bookrush.jteodoro.tec.br}"
upstream="${BOOKRUSH_GATEWAY_UPSTREAM:-http://127.0.0.1:18081}"
lock_file="${NGINX_LOCK:-/run/lock/bookrush-nginx.lock}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Execute como root (por exemplo: sudo bash $0)." >&2
  exit 1
fi
for command in nginx python3 flock mktemp cp mv; do
  command -v "$command" >/dev/null || { echo "Comando ausente: $command" >&2; exit 1; }
done
[[ -f "$config" ]] || { echo "Configuração não encontrada: $config" >&2; exit 1; }

mkdir -p "$(dirname "$lock_file")"
exec 9>"$lock_file"
flock -n 9 || { echo 'Outra atualização do Nginx está em andamento.' >&2; exit 1; }

nginx -t
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup="${config}.bookrush-gateway-backup.${timestamp}"
candidate="$(mktemp "$(dirname "$config")/.bookrush-gateway.XXXXXX")"
trap 'rm -f "$candidate"' EXIT
cp -p "$config" "$backup"
cp -p "$config" "$candidate"

CONFIG="$candidate" DOMAIN="$domain" UPSTREAM="$upstream" python3 - <<'PY'
import os
import re
from pathlib import Path

path = Path(os.environ["CONFIG"])
domain = os.environ["DOMAIN"]
upstream = os.environ["UPSTREAM"]
text = path.read_text()
needle = f"server_name {domain};"
start = 0
matches = []
while True:
    found = re.search(r"\bserver\s*\{", text[start:])
    if not found:
        break
    server = start + found.start()
    brace = start + found.end() - 1
    depth = 0
    end = None
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                break
    if end is None:
        raise SystemExit("bloco server incompleto")
    block = text[server:end]
    if needle in block:
        matches.append((server, end, block))
    start = end

if len(matches) != 1:
    raise SystemExit(f"esperado exatamente um virtual host com {needle!r}; encontrados {len(matches)}")

server_start, server_end, block = matches[0]
updated, count = re.subn(r"proxy_pass\s+http://127\.0\.0\.1:\d+\s*;", f"proxy_pass {upstream};", block)
if count == 0:
    raise SystemExit("o virtual host não possui proxy_pass local para atualizar")
replacement = text[:server_start] + updated + text[server_end:]
path.write_text(replacement)
print(f"virtual host atualizado: {domain}; upstreams alterados: {count}")
PY

if ! nginx -t -c "$candidate"; then
  echo "A nova configuração falhou; mantendo a configuração original." >&2
  exit 1
fi
cp -p "$candidate" "$config"
if ! nginx -t; then
  cp -p "$backup" "$config"
  nginx -t || true
  echo "Falha após instalar a configuração; backup restaurado: $backup" >&2
  exit 1
fi
nginx -s reload
echo "Gateway configurado em $upstream para $domain."
echo "Backup: $backup"
