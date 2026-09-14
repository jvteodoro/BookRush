#!/usr/bin/env bash
set -euo pipefail
books="${1:-1000}"
python3 - "$books" <<'PY'
import os, resource, sys, time
books = int(sys.argv[1])
seed = 1337
words = ("A deterministic fixture sentence with dialogue? Yes! Unicode café and 𝄞. ".split())
start = time.monotonic(); excerpts = 0; storage = 0
for book in range(books):
    text = " ".join(words * 40)
    storage += len(text.encode("utf-8"))
    for offset in range(0, len(text), 240):
        if offset + 80 <= len(text):
            excerpts += 1
elapsed = time.monotonic() - start
rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
print(f"seed={seed} books={books} excerpts={excerpts} seconds={elapsed:.3f} books_per_second={books/elapsed:.2f} excerpts_per_second={excerpts/elapsed:.2f} utf8_bytes={storage} max_rss_kb={rss} embeddings_per_second=0 (provider disabled)")
PY
