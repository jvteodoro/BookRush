#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
image=${ANALYTICS_RUNTIME_TEST_IMAGE:-bookrush/book-analytics-runtime:validation}
volume=${ANALYTICS_MODEL_VOLUME:-bookrush_analytics_models}
container="bookrush-analytics-models-test-$$"
tmp_dir=$(mktemp -d)

cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

docker build -t "$image" "$root_dir/services/book-analytics-runtime"
docker run -d --rm --name "$container" \
  -e ANALYTICS_OFFLINE=true \
  -v "${volume}:/var/lib/bookrush/models:ro" \
  "$image" >/dev/null

runtime_get() {
  docker exec "$container" python -c \
    'import sys,urllib.request; print(urllib.request.urlopen("http://127.0.0.1:8092" + sys.argv[1], timeout=10).read().decode())' \
    "$1"
}

runtime_post() {
  docker exec "$container" python -c \
    'import sys,urllib.request; request=urllib.request.Request("http://127.0.0.1:8092" + sys.argv[1], data=sys.argv[2].encode(), headers={"Content-Type":"application/json"}, method="POST"); print(urllib.request.urlopen(request, timeout=60).read().decode())' \
    "$1" "$2"
}

for _ in $(seq 1 120); do
  if runtime_get /health >"$tmp_dir/health.json"; then break; fi
  sleep 1
done
if ! test -s "$tmp_dir/health.json"; then
  echo "analytics runtime did not become healthy" >&2
  docker logs "$container" >&2 || true
  exit 1
fi

runtime_post /v1/language '{"text":"The old house stood beside the quiet harbor."}' >"$tmp_dir/language-en.json"
runtime_post /v1/language '{"text":"A casa antiga ficava perto do porto silencioso."}' >"$tmp_dir/language-pt.json"
runtime_post /v1/linguistic '{"language":"en","text":"The old house stood beside the quiet harbor."}' >"$tmp_dir/linguistic-en.json"
runtime_post /v1/linguistic '{"language":"pt","text":"A casa antiga ficava perto do porto silencioso."}' >"$tmp_dir/linguistic-pt.json"
runtime_post /v1/embeddings '{"texts":["The old house stood beside the quiet harbor.","A casa antiga ficava perto do porto silencioso."],"max_tokens":8192}' >"$tmp_dir/embeddings.json"
runtime_post /v1/nli '{"text":"The travelers reached the harbor before the storm.","hypothesis":"The passage describes an external event."}' >"$tmp_dir/nli.json"

python3 - "$tmp_dir" <<'PY'
import json
import math
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
def read(name):
    return json.loads((directory / name).read_text())

for name, language in (("language-en.json", "en"), ("language-pt.json", "pt")):
    result = read(name)
    assert result["status"] == "VALID", (name, result)
    assert result["language"] == language, (name, result)
    assert 0 <= result["confidence"] <= 1, (name, result)

for name, language in (("linguistic-en.json", "en"), ("linguistic-pt.json", "pt")):
    result = read(name)
    assert result["status"] == "VALID", (name, result)
    assert result["language"] == language, (name, result)
    assert result["values"], (name, result)

embeddings = read("embeddings.json")
assert embeddings["status"] == "VALID", embeddings
assert embeddings["model"] == "bge-m3-dense-v1", embeddings
assert embeddings["dimension"] == 1024, embeddings
assert len(embeddings["vectors"]) == 2, embeddings
for vector in embeddings["vectors"]:
    norm = math.sqrt(sum(float(value) ** 2 for value in vector))
    assert len(vector) == 1024 and abs(norm - 1.0) < 1e-5, norm

nli = read("nli.json")
assert nli["status"] == "VALID", nli
scores = [nli["entailment"], nli["neutral"], nli["contradiction"]]
assert all(0 <= value <= 1 for value in scores), nli
assert abs(sum(scores) - 1.0) < 1e-5, nli
print("analytics model runtime passed: fastText EN/PT, spaCy EN/PT, BGE-M3 1024-D and NLI")
PY
