#!/usr/bin/env bash
set -euo pipefail
count="${1:-100000}"
root="${TMPDIR:-/tmp}/bookrush-ingestion-benchmark"
rm -rf "$root"
mkdir -p "$root"
python3 - "$root" "$count" <<'PY'
import pathlib, sys, time
root, count = pathlib.Path(sys.argv[1]), int(sys.argv[2])
start = time.monotonic()
with (root/'records.ndjson').open('w', encoding='utf-8') as out:
    for i in range(count): out.write('{"externalId":"%d","title":"Synthetic %d"}\n' % (i, i))
elapsed = time.monotonic()-start
print(f'generated={count} seconds={elapsed:.3f} records_per_second={count/elapsed:.0f}')
PY
