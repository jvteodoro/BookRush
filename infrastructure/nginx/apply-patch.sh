#!/usr/bin/env bash
# Execute com sudo. O patch é aplicado exclusivamente ao nginx.conf do host.
set -Eeuo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
patch_file="$script_dir/bookrush-nginx.patch"
config=/etc/nginx/nginx.conf
auth_file=/etc/nginx/.htpasswd-bookrush-admin
backup=''
candidate=''
modified=0

if (( EUID != 0 )); then
    echo "Execute: sudo bash $0" >&2
    exit 1
fi
for command in nginx patch mktemp cp flock; do
    command -v "$command" >/dev/null || { echo "Comando necessário: $command" >&2; exit 1; }
done

# Evita duas execuções simultâneas deste script.
exec 9>/run/lock/bookrush-nginx.lock
flock -n 9 || { echo 'Outra aplicação do patch está em andamento.' >&2; exit 1; }
[[ -f "$config" && -r "$patch_file" ]] || { echo 'Configuração ou patch não encontrado.' >&2; exit 1; }

cleanup() {
    local status=$?
    trap - EXIT
    if (( modified )); then
        echo "Restaurando configuração de $backup" >&2
        if cp -p -- "$backup" "$config"; then
            nginx -t && nginx -s reload || echo 'Falha ao recarregar o original; verifique o Nginx.' >&2
        else
            echo "Falha na restauração! Backup disponível em $backup" >&2
        fi
    fi
    [[ -z "$candidate" ]] || rm -f -- "$candidate"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

nginx -t
candidate="$(mktemp /etc/nginx/.bookrush-candidate.XXXXXX)"
cp -p -- "$config" "$candidate"

if ! patch --batch --forward --fuzz=0 --dry-run "$candidate" "$patch_file" >/dev/null 2>&1; then
    if patch --batch --reverse --fuzz=0 --dry-run "$candidate" "$patch_file" >/dev/null 2>&1; then
        echo 'O patch já está aplicado. Nenhuma alteração realizada.'
        exit 0
    fi
    echo 'O patch não corresponde à configuração atual. Nenhuma alteração realizada.' >&2
    exit 1
fi

if [[ ! -s "$auth_file" ]]; then
    echo "Crie primeiro o usuário administrativo: sudo htpasswd -c $auth_file SEU_USUARIO" >&2
    echo 'Se htpasswd não estiver instalado, instale o pacote que fornece esse comando.' >&2
    exit 1
fi

patch --batch --forward --fuzz=0 --no-backup-if-mismatch "$candidate" "$patch_file"
nginx -t -c "$candidate"

backup="$(mktemp /etc/nginx/nginx.conf.bookrush-backup.XXXXXXXX)"
cp -p -- "$config" "$backup"
echo "Backup criado: $backup"
modified=1
cp -p -- "$candidate" "$config"
nginx -t
nginx -s reload
modified=0
echo 'Patch aplicado e recarga do Nginx solicitada com sucesso.'
echo "Para restaurar: sudo cp -p '$backup' '$config' && sudo nginx -t && sudo nginx -s reload"
