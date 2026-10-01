#!/usr/bin/env bash
# Explicit, operator-invoked preparation for Analytics model artifacts.
# Application startup never calls this script and never downloads model data.
set -Eeuo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cache_dir=${ANALYTICS_MODEL_CACHE_DIR:-"$root_dir/.analytics-model-cache"}
manifest=${ANALYTICS_MODEL_MANIFEST:-"$root_dir/services/book-analytics-service/config/model-artifacts-v1.json"}
mode=${1:---verify}

case "$mode" in
  --fetch|--verify) ;;
  *) echo "usage: $0 [--fetch|--verify]" >&2; exit 64 ;;
esac

command -v python3 >/dev/null || { echo 'python3 is required to read the JSON manifest' >&2; exit 69; }
if [[ "$mode" == --fetch ]]; then
  command -v curl >/dev/null || { echo 'curl is required for --fetch' >&2; exit 69; }
fi

mkdir -p "$cache_dir"
echo "Analytics model cache: $cache_dir" >&2
echo "Manifest: $manifest" >&2
[[ "$mode" == --fetch ]] && echo 'Downloading only the pinned immutable sources declared in the manifest.' >&2

ANALYTICS_MODEL_CACHE_DIR="$cache_dir" ANALYTICS_MODEL_MANIFEST="$manifest" ANALYTICS_MODEL_FETCH_MODE="$mode" python3 - <<'PY'
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile

cache = pathlib.Path(os.environ['ANALYTICS_MODEL_CACHE_DIR']).resolve()
manifest_path = pathlib.Path(os.environ['ANALYTICS_MODEL_MANIFEST']).resolve()
mode = os.environ['ANALYTICS_MODEL_FETCH_MODE']

with manifest_path.open(encoding='utf-8') as stream:
    manifest = json.load(stream)

if manifest.get('schemaVersion') != 'bookrush.analytics.model-artifacts.v1':
    raise SystemExit(f'unsupported model artifact manifest: {manifest_path}')

def source_url(artifact, relative_path):
    source = artifact['source']
    if source['type'] == 'url':
        if len(artifact['files']) != 1:
            raise RuntimeError(f"url artifact {artifact['id']} must contain exactly one file")
        return source['url']
    if source['type'] == 'huggingface':
        return f"https://huggingface.co/{source['repo']}/resolve/{source['revision']}/{relative_path}"
    raise RuntimeError(f"unsupported source type {source['type']}")

def digest(path):
    hasher = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            hasher.update(block)
    return hasher.hexdigest()

failures = []
for artifact in manifest['artifacts']:
    for item in artifact['files']:
        target = (cache / artifact['id'] / item['path']).resolve()
        if cache not in target.parents:
            raise RuntimeError(f"invalid path outside cache: {item['path']}")
        if mode == '--fetch' and not target.is_file():
            target.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(prefix=target.name + '.', dir=target.parent, delete=False) as temporary:
                temporary_path = pathlib.Path(temporary.name)
            try:
                subprocess.run(['curl', '--fail', '--location', '--retry', '3', '--connect-timeout', '20',
                                '--output', str(temporary_path), source_url(artifact, item['path'])], check=True)
                if digest(temporary_path).lower() != item['sha256'].lower():
                    raise RuntimeError(f"downloaded checksum mismatch for {artifact['id']}/{item['path']}")
                temporary_path.replace(target)
            finally:
                temporary_path.unlink(missing_ok=True)
        if not target.is_file():
            failures.append(f"missing {artifact['id']}/{item['path']}; run `make analytics-models-fetch`")
            continue
        actual = digest(target)
        if actual.lower() != item['sha256'].lower():
            failures.append(f"checksum mismatch {artifact['id']}/{item['path']}: expected {item['sha256']}, got {actual}")
            continue
        print(f"verified {artifact['id']}/{item['path']}")

if failures:
    print('Analytics model cache is incomplete or invalid:', file=sys.stderr)
    print(*failures, sep='\n', file=sys.stderr)
    raise SystemExit(1)
print('Analytics model artifacts verified.')
PY
