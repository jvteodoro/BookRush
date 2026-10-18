#!/usr/bin/env python3
"""Materialize the versioned EN/PT semantic prototype artifact from local BGE-M3."""
import argparse
import hashlib
import json
import math
import pathlib
import sys
import urllib.error
import urllib.request


def load_yaml(path):
    try:
        import yaml
    except ImportError as exc:
        raise SystemExit("PyYAML is required to materialize semantic prototypes") from exc
    with path.open(encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def post_embeddings(endpoint, texts):
    payload = json.dumps({"texts": texts, "max_tokens": 8192}).encode()
    request = urllib.request.Request(
        endpoint.rstrip("/") + "/v1/embeddings",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=300) as response:
        body = json.load(response)
    if body.get("status") != "VALID":
        raise RuntimeError(f"BGE runtime unavailable: {body.get('warning', body.get('status'))}")
    if body.get("model") != "bge-m3-dense-v1" or body.get("dimension") != 1024:
        raise RuntimeError("runtime did not return the pinned BGE-M3 1024-dimensional model")
    if len(body.get("vectors", [])) != len(texts):
        raise RuntimeError("embedding response length does not match request")
    return body["vectors"], body.get("input_hashes", [])


def normalize(vector):
    norm = math.sqrt(sum(float(value) ** 2 for value in vector))
    if not norm:
        raise RuntimeError("BGE returned a zero vector")
    return [float(value) / norm for value in vector]


def centroid(vectors):
    return normalize([sum(vector[index] for vector in vectors) / len(vectors) for index in range(1024)])


def canonical_bytes(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def materialize(config, endpoint):
    examples = []
    for concept in sorted(config["concepts"]):
        for language in ("en", "pt"):
            for ordinal, text in enumerate(config["concepts"][concept][language]):
                examples.append({"concept": concept, "language": language, "ordinal": ordinal, "text": text})

    embedded = []
    for offset in range(0, len(examples), 32):
        batch = examples[offset : offset + 32]
        vectors, hashes = post_embeddings(endpoint, [item["text"] for item in batch])
        if len(hashes) != len(batch):
            raise RuntimeError("runtime did not return input hashes for every example")
        for item, vector, input_hash in zip(batch, vectors, hashes):
            expected_hash = hashlib.sha256(item["text"].encode()).hexdigest()
            if input_hash != expected_hash:
                raise RuntimeError(f"input hash mismatch for {item['concept']}/{item['language']}/{item['ordinal']}")
            embedded.append({**item, "input_sha256": input_hash, "vector": normalize(vector)})

    prototypes = []
    for concept in sorted(config["concepts"]):
        for language in ("en", "pt", "multilingual"):
            selected = [item for item in embedded if item["concept"] == concept and (language == "multilingual" or item["language"] == language)]
            prototypes.append({
                "concept": concept,
                "language": language,
                "example_hashes": [item["input_sha256"] for item in selected],
                "vector": centroid([item["vector"] for item in selected]),
            })
    return {
        "artifact_type": "bookrush.semantic-prototype-set",
        "artifact_version": "semantic-prototype-set-v1.0.0",
        "embedding_model": "bge-m3-dense-v1",
        "dimension": 1024,
        "normalization": "l2",
        "centroid_formula": config["centroid_formula"],
        "source_spec": "semantic-prototypes-v1.yaml",
        "examples": [{key: item[key] for key in ("concept", "language", "ordinal", "input_sha256")} for item in embedded],
        "prototypes": prototypes,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", default="http://localhost:8092")
    parser.add_argument("--config", type=pathlib.Path, default=pathlib.Path("services/book-analytics-service/config/semantic-prototypes-v1.yaml"))
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    artifact = materialize(load_yaml(args.config), args.endpoint)
    artifact["artifact_sha256"] = hashlib.sha256(canonical_bytes(artifact)).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"materialized {len(artifact['examples'])} examples and {len(artifact['prototypes'])} prototypes")
    print(f"artifact_sha256={artifact['artifact_sha256']}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, urllib.error.URLError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
