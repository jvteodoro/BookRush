#!/usr/bin/env bash
set -Eeuo pipefail
env_file="${1:-.env}"
[[ -f "$env_file" ]] || { echo "Arquivo ausente: $env_file" >&2; exit 2; }
command -v openssl >/dev/null || { echo 'openssl ausente.' >&2; exit 2; }
token="$(grep -E '^BEHAVIOR_OUTBOX_SINK_TOKEN=' "$env_file" | cut -d= -f2- || true)"
[[ -n "$token" ]] || token="$(openssl rand -hex 32)"
tmp="$(mktemp "${env_file}.XXXXXX")"
chmod 600 "$tmp"
trap 'rm -f -- "$tmp"' EXIT
awk -v token="$token" '
  BEGIN { token_done=0; url_done=0 }
  /^BEHAVIOR_OUTBOX_SINK_TOKEN=/ { print "BEHAVIOR_OUTBOX_SINK_TOKEN=" token; token_done=1; next }
  /^BEHAVIOR_OUTBOX_SINK_URL=/ { print "BEHAVIOR_OUTBOX_SINK_URL=http:\/\/book-analytics-service:8091\/api\/internal\/v1\/behavior\/events"; url_done=1; next }
  { print }
  END {
    if (!url_done) print "BEHAVIOR_OUTBOX_SINK_URL=http://book-analytics-service:8091/api/internal/v1/behavior/events"
    if (!token_done) print "BEHAVIOR_OUTBOX_SINK_TOKEN=" token
  }
' "$env_file" >"$tmp"
mv -f -- "$tmp" "$env_file"
echo "Behavior outbox sink configurado em ${env_file}."
