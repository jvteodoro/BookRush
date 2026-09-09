#!/bin/sh
set -eu
# Restrict the alphabet so credentials can safely be rendered into JSON without a templating dependency.
case "${STORAGE_ACCESS_KEY:-}" in ''|*[!a-zA-Z0-9_-]*) echo 'Invalid STORAGE_ACCESS_KEY' >&2; exit 1;; esac
case "${STORAGE_SECRET_KEY:-}" in ''|*[!a-zA-Z0-9_-]*) echo 'Invalid STORAGE_SECRET_KEY' >&2; exit 1;; esac
umask 077
mkdir -p /data/filer /etc/seaweedfs
cat > /tmp/bookrush-s3.json <<EOF_CONFIG
{"identities":[{"name":"bookrush","credentials":[{"accessKey":"$STORAGE_ACCESS_KEY","secretKey":"$STORAGE_SECRET_KEY"}],"actions":["Admin","Read","Write","List","Tagging"]}]}
EOF_CONFIG
cat > /etc/seaweedfs/filer.toml <<'EOF_FILER'
[leveldb2]
enabled = true
dir = "/data/filer"
EOF_FILER
exec weed server -dir=/data -ip=seaweedfs -ip.bind=0.0.0.0 -master.volumeSizeLimitMB=128 -volume.max=32 -s3 -s3.config=/tmp/bookrush-s3.json
