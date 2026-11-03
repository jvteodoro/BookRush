#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

python3 - "$root_dir" "$tmp_dir/prototypes.json" <<'PY'
import hashlib
import json
import pathlib
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread

root = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        size = int(self.headers["Content-Length"])
        texts = json.loads(self.rfile.read(size))["texts"]
        vectors = []
        hashes = []
        for text in texts:
            seed = hashlib.sha256(text.encode()).digest()
            vector = [0.0] * 1024
            vector[seed[0] % 1024] = 1.0
            vectors.append(vector)
            hashes.append(hashlib.sha256(text.encode()).hexdigest())
        body = json.dumps({"status": "VALID", "model": "bge-m3-dense-v1", "dimension": 1024, "vectors": vectors, "input_hashes": hashes}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *_):
        pass

server = HTTPServer(("127.0.0.1", 0), Handler)
Thread(target=server.serve_forever, daemon=True).start()
subprocess.run([
    sys.executable, str(root / "scripts/materialize-semantic-prototypes.py"),
    "--endpoint", f"http://127.0.0.1:{server.server_port}", "--output", str(output)
], cwd=root, check=True)
artifact = json.loads(output.read_text())
assert artifact["embedding_model"] == "bge-m3-dense-v1"
assert artifact["dimension"] == 1024
assert len(artifact["examples"]) == 72
assert len(artifact["prototypes"]) == 36
assert all(abs(sum(value * value for value in p["vector"]) - 1.0) < 1e-5 for p in artifact["prototypes"])
assert len(artifact["artifact_sha256"]) == 64
checksum = artifact.pop("artifact_sha256")
assert hashlib.sha256(json.dumps(artifact, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest() == checksum
server.shutdown()
print("semantic prototype materialization passed")
PY
