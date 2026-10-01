#!/usr/bin/env python3
"""Measure local NLI latency and project processing time/cost.

The first request is reported separately because it includes model loading.
Subsequent requests measure steady state. No external provider is contacted.
"""
from __future__ import annotations
import argparse, json, statistics, time
from urllib.request import Request, urlopen

def call(url: str, text: str, hypothesis: str) -> tuple[str, float, str | None]:
    body = json.dumps({"text": text, "hypothesis": hypothesis}).encode()
    started = time.perf_counter()
    try:
        request = Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
        with urlopen(request, timeout=600) as response:
            payload = json.loads(response.read())
        return str(payload.get("status", "UNKNOWN")), time.perf_counter() - started, payload.get("warning")
    except Exception as exc:
        return "ERROR", time.perf_counter() - started, f"{type(exc).__name__}: {exc}"

def text_of(words: int) -> str:
    sentence = "The travelers studied the old map while the storm approached the distant harbor."
    return " ".join(sentence for _ in range(max(1, words // 13)))

def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * fraction))))
    return ordered[index]

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8092")
    parser.add_argument("--repetitions", type=int, default=5)
    parser.add_argument("--warmups", type=int, default=1)
    parser.add_argument("--hourly-cost", type=float, default=0.0, help="optional local machine cost per hour")
    parser.add_argument("--nli-calls", type=int, default=1, help="calls projected per excerpt")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    if args.repetitions < 1 or args.warmups < 0 or args.nli_calls < 1:
        raise SystemExit("repetitions, warmups and nli-calls must be positive")
    endpoint = args.base_url.rstrip("/") + "/v1/nli"
    hypothesis = "This passage contains an active conflict between characters or opposing forces."
    warmup = [call(endpoint, text_of(120), hypothesis) for _ in range(args.warmups)]
    rows = []
    for words in (120, 300, 800):
        samples = [call(endpoint, text_of(words), hypothesis) for _ in range(args.repetitions)]
        durations = [duration for status, duration, _ in samples if status == "VALID"]
        statuses = sorted({status for status, _, _ in samples})
        row = {"words": words, "statuses": statuses, "valid_samples": len(durations)}
        if durations:
            total = sum(durations)
            row.update({"mean_seconds": total / len(durations), "p50_seconds": percentile(durations, .50),
                        "p95_seconds": percentile(durations, .95), "throughput_calls_per_second": len(durations) / total})
            row["seconds_per_1000_calls"] = (total / len(durations)) * 1000
            row["cost_per_1000_calls"] = (row["seconds_per_1000_calls"] / 3600) * args.hourly_cost
            row["seconds_per_excerpt"] = row["mean_seconds"] * args.nli_calls
            row["minutes_per_100_excerpts"] = row["seconds_per_excerpt"] * 100 / 60
            row["cost_per_100_excerpts"] = (row["seconds_per_excerpt"] * 100 / 3600) * args.hourly_cost
        else:
            row["warnings"] = sorted({warning for _, _, warning in samples if warning})
        rows.append(row)
    result = {"endpoint": endpoint, "repetitions": args.repetitions, "warmups": args.warmups,
              "warmup_seconds": [round(duration, 4) for _, duration, _ in warmup],
              "nli_calls_per_excerpt": args.nli_calls, "hourly_cost": args.hourly_cost, "sizes": rows}
    if args.json: print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(f"NLI endpoint: {endpoint}")
        print("warmup: " + ", ".join(f"{duration:.3f}s" for _, duration, _ in warmup) if warmup else "warmup: disabled")
        for row in rows:
            print(f"{row['words']:>4} words | statuses={','.join(row['statuses'])} | "
                  f"mean={row.get('mean_seconds', float('nan')):.3f}s | p95={row.get('p95_seconds', float('nan')):.3f}s | "
                  f"calls/s={row.get('throughput_calls_per_second', 0):.3f} | "
                  f"{row.get('seconds_per_excerpt', 0):.2f}s/excerpt ({args.nli_calls} calls) | "
                  f"cost/100={row.get('cost_per_100_excerpts', 0):.4f}")

if __name__ == "__main__":
    main()
