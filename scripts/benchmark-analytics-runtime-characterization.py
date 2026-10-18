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
    parser.add_argument("--batch-size", type=int, default=4)
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
    timings: dict[str, list[float]] = {"embeddings": [], "nli": []}
    counts = {"embeddings": 0, "nli": 0}
    statuses = {"embeddings": [], "nli": []}

    for offset in range(0, len(samples), args.batch_size):
        batch = samples[offset : offset + args.batch_size]
        elapsed, result = post(endpoint + "/v1/embeddings", {"texts": [item["text"] for item in batch]})
        timings["embeddings"].append(elapsed)
        counts["embeddings"] += len(batch)
        statuses["embeddings"].append(result.get("status"))

        nli_items = [
            {"id": item["id"], "text": item["text"], "hypothesis": args.nli_hypothesis}
            for item in batch
        ]
        elapsed, result = post(
            endpoint + "/v1/nli/batch",
            {"items": nli_items, "batch_size": args.batch_size},
        )
        timings["nli"].append(elapsed)
        counts["nli"] += len(batch)
        statuses["nli"].append(result.get("status"))

    summary = {
        "samples": len(samples),
        "batch_size": args.batch_size,
        "measure": "HTTP request wall time to model runtime; excludes pipeline DB/I/O",
        "operations": {},
    }
    for operation, values in timings.items():
        summary["operations"][operation] = {
            "requests": len(values),
            "items": counts[operation],
            "status_counts": {status: statuses[operation].count(status) for status in sorted(set(statuses[operation]))},
            "total_ms": round(sum(values), 1),
            "mean_ms": round(statistics.mean(values), 1),
            "p50_ms": round(percentile(values, 0.50), 1),
            "p95_ms": round(percentile(values, 0.95), 1),
            "per_item_mean_ms": round(sum(values) / counts[operation], 1),
        }
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
