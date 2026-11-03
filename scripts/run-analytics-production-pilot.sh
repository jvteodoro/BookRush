#!/usr/bin/env bash
set -euo pipefail

# Read-only pilot over Gutenberg works already present in the catalog snapshot.
root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
postgres_container=${PRODUCTION_POSTGRES_CONTAINER:-bookrush-postgres-1}
image=${ANALYTICS_RUNTIME_TEST_IMAGE:-bookrush/book-analytics-runtime:validation}
volume=${ANALYTICS_MODEL_VOLUME:-bookrush_analytics_models}
port=${ANALYTICS_PILOT_PORT:-18094}
output=${ANALYTICS_PILOT_OUTPUT:-/tmp/bookrush-analytics-production-pilot.json}
max_books=${PRODUCTION_PILOT_MAX_BOOKS:-8}
tmp_dir=$(mktemp -d)
container="bookrush-analytics-production-pilot-$$"
trap 'docker rm -f "$container" >/dev/null 2>&1 || true; rm -rf "$tmp_dir"' EXIT

for command in curl docker python3; do
  command -v "$command" >/dev/null || { echo "production pilot: comando ausente: $command" >&2; exit 2; }
done

docker exec "$postgres_container" psql -U bookrush -d bookrush -Atc \
  "select b.id || E'\\t' || ei.identifier_value || E'\\t' || coalesce(b.original_language, 'en')
     from catalog.book b
     join lateral (
       select identifier_value from catalog.external_identifier ei
       join catalog.edition e on e.id = ei.edition_id
       where e.book_id = b.id and ei.identifier_type = 'GUTENBERG_ID'
       order by ei.created_at limit 1
     ) ei on true
     where b.status in ('ACTIVE','REVIEW_REQUIRED')
     order by b.created_at" | head -n "$max_books" > "$tmp_dir/catalog.tsv"
test -s "$tmp_dir/catalog.tsv" || { echo 'production pilot: nenhum livro Gutenberg no snapshot' >&2; exit 1; }

while IFS=$'\t' read -r book_id gutenberg_id language; do
  [[ "$gutenberg_id" =~ ^[0-9]+$ ]] || continue
  text_file="$tmp_dir/$book_id.txt"
  curl --http1.1 -fsSL --retry 2 --connect-timeout 10 \
    "https://www.gutenberg.org/cache/epub/${gutenberg_id}/pg${gutenberg_id}.txt" \
    -o "$text_file" || curl -fsSL --retry 2 --connect-timeout 10 \
    "https://www.gutenberg.org/files/${gutenberg_id}/${gutenberg_id}-0.txt" \
    -o "$text_file"
  BOOK_ID="$book_id" LANGUAGE="$language" TEXT_FILE="$text_file" \
    python3 - "$tmp_dir/books.jsonl" <<'PY'
import json, os, re, sys
text = open(os.environ["TEXT_FILE"], encoding="utf-8", errors="replace").read()
start = re.search(r"\*\*\* START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK.*?\*\*\*", text, re.S | re.I)
end = re.search(r"\*\*\* END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK.*?\*\*\*", text, re.S | re.I)
if start:
    text = text[start.end():]
if end:
    text = text[:end.start()]
text = re.sub(r"\s+", " ", text).strip()
# Bound each work to a reproducible real-text window for the capacity pilot;
# this is not the full-book analytics job.
text = text[:2000]
if len(text) >= 200:
    with open(sys.argv[1], "a", encoding="utf-8") as out:
        out.write(json.dumps({"book_id": os.environ["BOOK_ID"], "language": os.environ["LANGUAGE"], "text": text}, ensure_ascii=False) + "\n")
PY
done < "$tmp_dir/catalog.tsv"

test -s "$tmp_dir/books.jsonl" || { echo 'production pilot: nenhum texto utilizável' >&2; exit 1; }
docker build -t "$image" "$root_dir/services/book-analytics-runtime" >/dev/null
docker run -d --rm --name "$container" -p "${port}:8092" -e ANALYTICS_OFFLINE=true \
  -v "${volume}:/var/lib/bookrush/models:ro" "$image" >/dev/null
for _ in $(seq 1 120); do
  if curl -fsS "http://127.0.0.1:${port}/health" >/dev/null; then break; fi
  sleep 1
done
python3 "$root_dir/scripts/run-analytics-pilot.py" \
  --base-url "http://127.0.0.1:${port}" \
  --input-jsonl "$tmp_dir/books.jsonl" \
  --embedding-batch-size 4 \
  --source "production-catalog-snapshot+gutenberg-public" \
  --output "$output"
