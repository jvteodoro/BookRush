#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
image=${ANALYTICS_RUNTIME_TEST_IMAGE:-bookrush/book-analytics-runtime:validation}
volume=${ANALYTICS_MODEL_VOLUME:-bookrush_analytics_models}
port=${ANALYTICS_RUNTIME_TEST_PORT:-18092}
container="bookrush-analytics-models-test-$$"
tmp_dir=$(mktemp -d)
run_args=(-p "${port}:8092")

# Jenkins runs the agent in a container while Docker creates this runtime as a
# sibling through the host daemon. Share the agent namespace there so the
# loopback probes reach the runtime; keep the published port for local runs.
if [[ -n "${HOSTNAME:-}" ]] && docker inspect "$HOSTNAME" >/dev/null 2>&1; then
  run_args=(--network "container:$HOSTNAME")
fi

cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

docker build -t "$image" "$root_dir/services/book-analytics-runtime"
docker run -d --rm --name "$container" "${run_args[@]}" \
  -e ANALYTICS_OFFLINE=true \
  -v "${volume}:/var/lib/bookrush/models:ro" \
  "$image" >/dev/null

for _ in $(seq 1 120); do
  if curl -fsS "http://127.0.0.1:${port}/health" >"$tmp_dir/health.json"; then break; fi
  sleep 1
done
if ! test -s "$tmp_dir/health.json"; then
  echo "analytics runtime did not become healthy" >&2
  docker logs "$container" >&2 || true
  exit 1
fi

curl -fsS -X POST "http://127.0.0.1:${port}/v1/language" -H 'Content-Type: application/json' \
  --data '{"text":"The old house stood beside the quiet harbor."}' >"$tmp_dir/language-en.json"
curl -fsS -X POST "http://127.0.0.1:${port}/v1/language" -H 'Content-Type: application/json' \
  --data '{"text":"A casa antiga ficava perto do porto silencioso."}' >"$tmp_dir/language-pt.json"
curl -fsS -X POST "http://127.0.0.1:${port}/v1/linguistic" -H 'Content-Type: application/json' \
  --data '{"language":"en","text":"The old house stood beside the quiet harbor."}' >"$tmp_dir/linguistic-en.json"
curl -fsS -X POST "http://127.0.0.1:${port}/v1/linguistic" -H 'Content-Type: application/json' \
  --data '{"language":"pt","text":"A casa antiga ficava perto do porto silencioso."}' >"$tmp_dir/linguistic-pt.json"
curl -fsS -X POST "http://127.0.0.1:${port}/v1/embeddings" -H 'Content-Type: application/json' \
  --data '{"texts":["The old house stood beside the quiet harbor.","A casa antiga ficava perto do porto silencioso."],"max_tokens":8192}' >"$tmp_dir/embeddings.json"
curl -fsS -X POST "http://127.0.0.1:${port}/v1/nli" -H 'Content-Type: application/json' \
  --data '{"text":"The travelers reached the harbor before the storm.","hypothesis":"The passage describes an external event."}' >"$tmp_dir/nli.json"

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
