#!/usr/bin/env bash
set -Eeuo pipefail

usage() { echo "Uso: secrets.sh encrypt|decrypt INPUT OUTPUT | run-compose --env-enc FILE [--profile NAME...]"; }
command -v sops >/dev/null || { echo 'sops ausente; instale uma versão fixada no host.' >&2; exit 2; }
command -v age >/dev/null || { echo 'age ausente; instale age/age-keygen no host.' >&2; exit 2; }
: "${SOPS_CONFIG:?Defina SOPS_CONFIG fora do repositório}"
: "${AGE_KEY_FILE:?Defina AGE_KEY_FILE fora do repositório}"
[[ -f "$SOPS_CONFIG" && -f "$AGE_KEY_FILE" ]] || { echo 'Configuração SOPS ou chave age ausente.' >&2; exit 2; }
export SOPS_CONFIG
mode="${1:-}"; shift || true
case "$mode" in
  encrypt)
    [[ $# -eq 2 ]] || { usage >&2; exit 2; }
    input="$1"; output="$2"; [[ -f "$input" ]] || { echo "Entrada ausente: $input" >&2; exit 2; }
    umask 077; sops --config "$SOPS_CONFIG" --encrypt --input-type dotenv --output-type dotenv "$input" > "$output"; chmod 600 "$output";;
  decrypt)
    [[ $# -eq 2 ]] || { usage >&2; exit 2; }
    input="$1"; output="$2"; [[ -f "$input" ]] || { echo "Entrada ausente: $input" >&2; exit 2; }
    umask 077; SOPS_AGE_KEY_FILE="$AGE_KEY_FILE" sops --config "$SOPS_CONFIG" --decrypt --input-type dotenv --output-type dotenv "$input" > "$output"; chmod 600 "$output";;
  run-compose)
    [[ "${1:-}" == "--env-enc" && -n "${2:-}" ]] || { usage >&2; exit 2; }
    encrypted="$2"; shift 2; [[ -f "$encrypted" ]] || { echo "Arquivo cifrado ausente: $encrypted" >&2; exit 2; }
    runtime_env="$(mktemp "${TMPDIR:-/tmp}/bookrush-secret.XXXXXX")"; trap 'rm -f -- "$runtime_env"' EXIT; chmod 600 "$runtime_env"
    SOPS_AGE_KEY_FILE="$AGE_KEY_FILE" sops --config "$SOPS_CONFIG" --decrypt --input-type dotenv --output-type dotenv "$encrypted" > "$runtime_env"
    docker compose --env-file "$runtime_env" -f infrastructure/compose.yaml "$@"
    ;;
  *) usage >&2; exit 2;;
esac
