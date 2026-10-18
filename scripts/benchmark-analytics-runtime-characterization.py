#!/usr/bin/env python3
"""Measure model-only latency for real analytics excerpt samples.

Input is JSONL with ``{"id": ..., "text": ...}`` records.  The script is
deliberately independent of the production database: callers can extract a
small, non-secret sample from the analytics store and feed it to a local or
temporary runtime container.
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
import urllib.request


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def post(url: str, payload: dict) -> tuple[float, dict]:
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=900) as response:
        result = json.load(response)
    return (time.perf_counter() - started) * 1000.0, result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", default="http://127.0.0.1:18092")
    parser.add_argument("--batch-size", type=int, default=4,
                        help="single batch size when --batch-sizes is not supplied")
    parser.add_argument("--batch-sizes", default=None,
                        help="comma-separated batch sizes for a throughput sweep")
    parser.add_argument("--nli-hypothesis", default="This excerpt contains a meaningful narrative event.")
    parser.add_argument("--limit", type=int, default=32)
    args = parser.parse_args()

    samples = []
    for line in sys.stdin:
        if not line.strip():
            continue
        record = json.loads(line)
        if not record.get("text"):
            continue
        samples.append({"id": str(record.get("id", len(samples))), "text": record["text"]})
        if len(samples) >= args.limit:
            break
    if not samples:
        raise SystemExit("no JSONL samples supplied")

    endpoint = args.endpoint.rstrip("/")
    batch_sizes = ([int(value) for value in args.batch_sizes.split(",") if value.strip()]
                   if args.batch_sizes else [args.batch_size])
    runs = {}
    baselines = {"embeddings": {}, "nli": {}}
    max_deltas = {"embeddings": {}, "nli": {}}
    valid_comparisons = {"embeddings": 0, "nli": 0}
    warnings = {}
    for batch_size in batch_sizes:
        timings: dict[str, list[float]] = {"embeddings": [], "nli": []}
        counts = {"embeddings": 0, "nli": 0}
        statuses = {"embeddings": [], "nli": []}
        for offset in range(0, len(samples), batch_size):
            batch = samples[offset : offset + batch_size]
            elapsed, result = post(endpoint + "/v1/embeddings", {"texts": [item["text"] for item in batch]})
            timings["embeddings"].append(elapsed)
            counts["embeddings"] += len(batch)
            statuses["embeddings"].append(result.get("status"))
            vectors = result.get("vectors", [])
            if result.get("status") != "VALID":
                warnings.setdefault(f"{batch_size}:embeddings", result.get("warning"))
            for item, vector in zip(batch, vectors):
                key = item["id"]
                if key in baselines["embeddings"]:
                    max_deltas["embeddings"][key] = max(
                        max_deltas["embeddings"].get(key, 0.0),
                        max(abs(float(a) - float(b)) for a, b in zip(baselines["embeddings"][key], vector)),
                    )
                    valid_comparisons["embeddings"] += 1
                else:
                    baselines["embeddings"][key] = vector

            nli_items = [{"id": item["id"], "text": item["text"],
                          "hypothesis": args.nli_hypothesis} for item in batch]
            elapsed, result = post(endpoint + "/v1/nli/batch",
                                   {"items": nli_items, "batch_size": batch_size})
            timings["nli"].append(elapsed)
            counts["nli"] += len(batch)
            statuses["nli"].append(result.get("status"))
            if result.get("status") != "VALID":
                warnings.setdefault(f"{batch_size}:nli", result.get("warning"))
            for row in result.get("results", []):
                key = row.get("id")
                scores = tuple(float(row.get(name, 0.0)) for name in
                               ("entailment", "neutral", "contradiction"))
                if row.get("status") != "VALID":
                    continue
                if key in baselines["nli"]:
                    max_deltas["nli"][key] = max(
                        max_deltas["nli"].get(key, 0.0),
                        max(abs(a - b) for a, b in zip(baselines["nli"][key], scores)),
                    )
                    valid_comparisons["nli"] += 1
                else:
                    baselines["nli"][key] = scores
        runs[str(batch_size)] = {}
        for operation, values in timings.items():
            runs[str(batch_size)][operation] = {
                "requests": len(values), "items": counts[operation],
                "status_counts": {status: statuses[operation].count(status)
                                   for status in sorted(set(statuses[operation]))},
                "total_ms": round(sum(values), 1), "mean_ms": round(statistics.mean(values), 1),
                "p50_ms": round(percentile(values, 0.50), 1),
                "p95_ms": round(percentile(values, 0.95), 1),
                "per_item_mean_ms": round(sum(values) / counts[operation], 1),
            }
    summary = {
        "samples": len(samples), "batch_sizes": batch_sizes,
        "measure": "HTTP request wall time to model runtime; excludes pipeline DB/I/O",
        "runs": runs,
        "equivalence": {
            "embedding_max_abs_delta": round(max(max_deltas["embeddings"].values(), default=0.0), 8),
            "nli_max_abs_delta": round(max(max_deltas["nli"].values(), default=0.0), 8),
            "valid_comparisons": valid_comparisons,
            "baseline": "first batch-size run",
        },
        "warnings": warnings,
    }
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
