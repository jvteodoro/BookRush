#!/usr/bin/env bash
set -Eeuo pipefail

# Controlled emergency-account lifecycle. This script is never called by
# Jenkins and never stores the emergency password or OTP seed in Git.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"
command_name=''
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2 ;;
    prepare|arm|status|revoke) command_name="$1"; shift ;;
    -h|--help) echo 'Uso: break-glass.sh [--env-file FILE] prepare|arm|status|revoke'; exit 0 ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$command_name" ]] || { echo 'Informe prepare, arm, status ou revoke.' >&2; exit 2; }
[[ -f "$env_file" ]] || { echo "Arquivo ausente: $env_file" >&2; exit 2; }
set -a
. "$env_file"
set +a
: "${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD ausente no ambiente protegido}"
: "${BREAK_GLASS_USERNAME:?BREAK_GLASS_USERNAME ausente no ambiente protegido}"
[[ "$BREAK_GLASS_USERNAME" != admin && "$BREAK_GLASS_USERNAME" != root ]] || { echo 'Nome break-glass padrão não permitido.' >&2; exit 2; }
[[ "${BREAK_GLASS_CONFIRM:-}" == I_UNDERSTAND ]] || { echo 'Defina BREAK_GLASS_CONFIRM=I_UNDERSTAND.' >&2; exit 2; }
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }

compose=(docker compose --project-name bookrush --env-file "$env_file" -f "$repo_dir/infrastructure/compose.yaml")
admin() { "${compose[@]}" exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
admin config credentials --server http://127.0.0.1:8080 --realm master \
  --user "${KEYCLOAK_ADMIN:-admin}" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null
user_id="$(admin get users -r bookrush-platform -q username="$BREAK_GLASS_USERNAME" \
  --fields id --format csv --noquotes | head -n1 || true)"
group_id="$(admin get groups -r bookrush-platform -q search=platform-admins \
  --fields id --format csv --noquotes | head -n1)"
test -n "$group_id"

case "$command_name" in
  prepare)
    : "${BREAK_GLASS_PASSWORD:?BREAK_GLASS_PASSWORD ausente; forneça-a somente pelo cofre}"
    [[ "$BREAK_GLASS_PASSWORD" != "$KEYCLOAK_ADMIN_PASSWORD" ]] || { echo 'Senhas devem ser diferentes.' >&2; exit 2; }
    if [[ -z "$user_id" ]]; then
      admin create users -r bookrush-platform -s username="$BREAK_GLASS_USERNAME" \
        -s enabled=false -s emailVerified=false >/dev/null
      user_id="$(admin get users -r bookrush-platform -q username="$BREAK_GLASS_USERNAME" \
        --fields id --format csv --noquotes | head -n1)"
    fi
    admin update "users/$user_id" -r bookrush-platform -s enabled=false \
      -s 'requiredActions=["CONFIGURE_TOTP"]' >/dev/null
    admin set-password -r bookrush-platform --userid "$user_id" \
      --new-password "$BREAK_GLASS_PASSWORD" >/dev/null
    admin update "users/$user_id/groups/$group_id" -r bookrush-platform >/dev/null
    echo 'Conta preparada e DESABILITADA; registre o segredo no cofre e faça o reenrolamento OTP independente.'
    ;;
  arm)
    [[ -n "$user_id" ]] || { echo 'Conta ausente; execute prepare primeiro.' >&2; exit 1; }
    admin update "users/$user_id" -r bookrush-platform -s enabled=true >/dev/null
    echo 'Conta habilitada somente para a janela de emergência; execute revoke ao terminar.'
    ;;
  status)
    [[ -n "$user_id" ]] || { echo 'Conta break-glass: ausente'; exit 0; }
    admin get "users/$user_id" -r bookrush-platform \
      --fields username,enabled,emailVerified,requiredActions --format csv --noquotes
    ;;
  revoke)
    [[ -n "$user_id" ]] || { echo 'Conta break-glass: ausente'; exit 0; }
    admin update "users/$user_id" -r bookrush-platform -s enabled=false >/dev/null
    admin delete "users/$user_id/sessions" -r bookrush-platform >/dev/null 2>&1 || true
    echo 'Conta desabilitada e sessões encerradas; rotacione o segredo no cofre e registre o incidente.'
    ;;
esac
