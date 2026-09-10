#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Instala todos os virtual hosts BookRush como módulos independentes.
# Uso: sudo bash infrastructure/nginx/install-bookrush-vhosts.sh

if (( EUID != 0 )); then
    echo "Execute como root: sudo bash $0" >&2
    exit 1
fi
for command in nginx python3 install mktemp cp mv rm mkdir flock date; do
    command -v "$command" >/dev/null || { echo "Comando ausente: $command" >&2; exit 1; }
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
config="${NGINX_CONFIG:-/etc/nginx/nginx.conf}"
module_dir="${NGINX_BOOKRUSH_MODULE_DIR:-/etc/nginx/conf.d}"
lock_file="${NGINX_LOCK:-/run/lock/bookrush-nginx.lock}"
source_dir="$script_dir/conf.d"
[[ -f "$config" && -d "$source_dir" ]] || { echo 'Configuração principal ou diretório de módulos não encontrado.' >&2; exit 1; }

mkdir -p "$(dirname "$lock_file")" "$module_dir"
exec 9>"$lock_file"
flock -n 9 || { echo 'Outra atualização do Nginx está em andamento.' >&2; exit 1; }

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup="${config}.bookrush-vhosts-backup.${timestamp}"
module_backup_dir="${module_dir}/.bookrush-vhosts-backup.${timestamp}"
candidate="$(mktemp "${config}.bookrush-candidate.XXXXXX")"
installed=0
shopt -s nullglob
existing_modules=("$module_dir"/bookrush-*.conf)
source_modules=("$source_dir"/bookrush-*.conf)
shopt -u nullglob
(( ${#source_modules[@]} > 0 )) || { echo 'Nenhum módulo BookRush encontrado.' >&2; exit 1; }

cleanup() {
    local status=$?
    trap - EXIT
    rm -f -- "$candidate"
    if (( status != 0 && installed )); then
        cp -p -- "$backup" "$config"
        rm -f -- "$module_dir"/bookrush-*.conf
        if [[ -d "$module_backup_dir" ]]; then
            cp -p -- "$module_backup_dir"/bookrush-*.conf "$module_dir"/ 2>/dev/null || true
        fi
        nginx -t || true
        echo "Falha; configuração principal restaurada de $backup" >&2
    fi
    exit "$status"
}
trap cleanup EXIT

cp -p -- "$config" "$backup"
mkdir -p "$module_backup_dir"
for module in "${existing_modules[@]}"; do
    cp -p -- "$module" "$module_backup_dir/"
done

CONFIG="$config" CANDIDATE="$candidate" python3 - <<'PY'
import os
import re
from pathlib import Path

source = Path(os.environ["CONFIG"]).read_text()
targets = {
    "bookrush.jteodoro.tec.br",
    "admin.bookrush.jteodoro.tec.br",
    "admin-bookrush.jteodoro.tec.br",
    "jenkins-bookrush.jteodoro.tec.br",
    "keycloak-bookrush.jteodoro.tec.br",
    "docs-bookrush.jteodoro.tec.br",
}
blocks = []
cursor = 0
while True:
    match = re.search(r"\bserver\s*\{", source[cursor:])
    if not match:
        break
    start = cursor + match.start()
    brace = cursor + match.end() - 1
    depth = 0
    end = None
    for index in range(brace, len(source)):
        if source[index] == "{": depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                break
    if end is None:
        raise SystemExit("bloco server incompleto")
    if any(f"server_name {domain}" in source[start:end] for domain in targets):
        blocks.append((start, end))
    cursor = end

for start, end in reversed(blocks):
    source = source[:start] + source[end:]
Path(os.environ["CANDIDATE"]).write_text(source)
print(f"blocos BookRush removidos do arquivo principal: {len(blocks)}")
PY

rm -f -- "$module_dir"/bookrush-*.conf
for module in "${source_modules[@]}"; do
    install -m 0644 "$module" "$module_dir/$(basename "$module")"
done
installed=1

nginx -t -c "$candidate"
mv -f -- "$candidate" "$config"
nginx -t
nginx -s reload
installed=0
echo "Virtual hosts BookRush instalados em $module_dir"
echo "Backup: $backup"
