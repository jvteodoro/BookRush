#!/usr/bin/env bash
set -Eeuo pipefail

if [[ -r /etc/os-release ]]; then . /etc/os-release; else echo 'Não foi possível identificar o sistema operacional.' >&2; exit 1; fi
if [[ "${ID:-}" != ubuntu ]]; then echo "Este instalador suporta Ubuntu; detectado: ${ID:-desconhecido}." >&2; exit 1; fi

as_root() { if (( EUID == 0 )); then "$@"; else sudo "$@"; fi; }
as_root apt-get update
as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  ca-certificates curl git jq openssl python3 python3-venv nginx apache2-utils certbot

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1 || ! docker buildx version >/dev/null 2>&1; then
  as_root install -m 0755 -d /etc/apt/keyrings
  if [[ ! -s /etc/apt/keyrings/docker.asc ]]; then
    as_root curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    as_root chmod a+r /etc/apt/keyrings/docker.asc
  fi
  arch="$(dpkg --print-architecture)"
  codename="${VERSION_CODENAME:-$(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")}"
  printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' "$arch" "$codename" | as_root tee /etc/apt/sources.list.d/docker.list >/dev/null
  as_root apt-get update
  as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

as_root systemctl enable --now docker
as_root systemctl enable nginx
invoking_user="${SUDO_USER:-${USER:-}}"
if [[ -n "$invoking_user" && "$invoking_user" != root ]]; then
  as_root usermod -aG docker "$invoking_user"
  echo "Usuário $invoking_user adicionado ao grupo docker; abra uma nova sessão para aplicar." >&2
fi
docker_bin=(docker)
if ! docker info >/dev/null 2>&1; then docker_bin=(sudo docker); fi
"${docker_bin[@]}" compose version
nginx -v 2>&1
echo 'Dependências Ubuntu instaladas.'
