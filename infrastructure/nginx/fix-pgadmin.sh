#!/usr/bin/env bash
# Corrige o cabeçalho que causa redirecionamento infinito no pgAdmin.
set -Eeuo pipefail
umask 077
(( EUID == 0 )) || { echo "Execute: sudo bash $0" >&2; exit 1; }
for tool in python3 nginx flock; do
    command -v "$tool" >/dev/null || { echo "Comando necessário: $tool" >&2; exit 1; }
done
exec 9>/run/lock/bookrush-nginx.lock
flock -n 9 || { echo 'Outra alteração do BookRush está em andamento.' >&2; exit 1; }
nginx -t
python3 - <<'PY'
from pathlib import Path
import shutil
import subprocess
import tempfile

config = Path('/etc/nginx/nginx.conf')
original = config.read_text()
start = original.index('server_name admin.bookrush.jteodoro.tec.br;')
end = original.find('server_name ', start + 1)
end = len(original) if end == -1 else end
section = original[start:end]
old = 'proxy_set_header X-Script-Name /;'
new = 'proxy_set_header X-Script-Name "";'
if old not in section and new in section:
    print('Correção já aplicada.')
    raise SystemExit(0)
if section.count(old) != 1:
    raise SystemExit('Configuração inesperada; nenhuma alteração realizada.')
updated = original[:start] + section.replace(old, new) + original[end:]
with tempfile.NamedTemporaryFile(prefix='nginx.conf.pgadmin-backup.', dir='/etc/nginx', delete=False) as file:
    backup = Path(file.name)
shutil.copy2(config, backup)
print(f'Backup: {backup}', flush=True)
try:
    config.write_text(updated)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['nginx', '-s', 'reload'], check=True)
except BaseException:
    shutil.copy2(backup, config)
    print('Configuração original restaurada.', flush=True)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['nginx', '-s', 'reload'], check=True)
    raise
print('Correção aplicada e recarga solicitada.')
PY
