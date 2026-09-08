#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
(( EUID == 0 )) || { echo "Execute: sudo bash $0" >&2; exit 1; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec 9>/run/lock/bookrush-nginx.lock
flock -n 9 || { echo 'Outra alteração do BookRush está em andamento.' >&2; exit 1; }
nginx -t
python3 - "$script_dir/bookrush-jenkins.conf.example" <<'PY'
from pathlib import Path
import sys, shutil, tempfile, subprocess
config = Path('/etc/nginx/nginx.conf')
original = config.read_text()
if 'server_name jenkins-bookrush.jteodoro.tec.br;' in original:
    print('Virtual host Jenkins já existe; nenhuma alteração.')
    raise SystemExit(0)
if original.count('http {') != 1:
    raise SystemExit('Bloco http inesperado; nenhuma alteração.')
addition = Path(sys.argv[1]).read_text()
updated = original.replace('http {', 'http {\n' + addition, 1)
with tempfile.NamedTemporaryFile(prefix='nginx.conf.jenkins-backup.', dir='/etc/nginx', delete=False) as f:
    backup = Path(f.name)
shutil.copy2(config, backup)
print(f'Backup: {backup}', flush=True)
try:
    config.write_text(updated)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['nginx', '-s', 'reload'], check=True)
except BaseException:
    shutil.copy2(backup, config)
    subprocess.run(['nginx', '-t'], check=True)
    subprocess.run(['nginx', '-s', 'reload'], check=True)
    raise
print('Virtual host instalado.')
PY
