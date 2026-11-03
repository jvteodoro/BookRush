#!/usr/bin/env python3
"""Summarize PT-text NLI scores under Portuguese and English hypotheses."""

from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    args = parser.parse_args()
    deltas = {}
    ids = set()
    for line_number, line in enumerate(args.input.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        required = {"id", "key", "pt_entailment", "en_entailment"}
        if not required <= row.keys():
            raise SystemExit(f"line {line_number}: missing fields {sorted(required - row.keys())}")
        if row["id"] in ids:
            raise SystemExit(f"line {line_number}: duplicate id {row['id']}")
        ids.add(row["id"])
        pt = float(row["pt_entailment"])
        en = float(row["en_entailment"])
        if not 0 <= pt <= 1 or not 0 <= en <= 1:
            raise SystemExit(f"line {line_number}: entailment must be in [0,1]")
        deltas.setdefault(row["key"], []).append(en - pt)
    if not deltas:
        raise SystemExit("no comparison rows")
    summary = {
        key: {
            "samples": len(values),
            "mean_delta_en_minus_pt": round(statistics.mean(values), 6),
            "mean_absolute_delta": round(statistics.mean(abs(value) for value in values), 6),
        }
        for key, values in sorted(deltas.items())
    }
    print(json.dumps({"rows": len(ids), "hypotheses": summary}, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
