#!/usr/bin/env python3
"""Validate the versioned human-review JSONL required by NLI Gate 5."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("--hypotheses", type=Path, default=Path(
        "services/book-analytics-service/src/main/resources/nli-hypotheses-v1.json"))
    parser.add_argument("--min-per-language", type=int, default=50)
    args = parser.parse_args()

    catalog = json.loads(args.hypotheses.read_text(encoding="utf-8"))
    expected = {
        language: {(item["kind"], item["key"]) for item in items}
        for language, items in catalog["sets"].items()
    }
    rows = []
    ids = set()
    for line_number, line in enumerate(args.input.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        required = {"id", "language", "text", "source", "reviewer", "labels"}
        missing = required - row.keys()
        if missing:
            raise SystemExit(f"line {line_number}: missing fields {sorted(missing)}")
        if row["id"] in ids:
            raise SystemExit(f"line {line_number}: duplicate id {row['id']}")
        ids.add(row["id"])
        language = row["language"].lower()
        if language not in expected:
            raise SystemExit(f"line {line_number}: unsupported language {language}")
        if not isinstance(row["text"], str) or len(row["text"].strip()) < 80:
            raise SystemExit(f"line {line_number}: text is too short for review")
        labels = row["labels"]
        if set(labels) != {"narrative", "emotion"}:
            raise SystemExit(f"line {line_number}: labels must contain narrative and emotion")
        observed = set()
        for kind, values in labels.items():
            if set(values) != {key for expected_kind, key in expected[language] if expected_kind == kind}:
                raise SystemExit(f"line {line_number}: incomplete {kind} labels")
            if any(value not in (1, 2, 3, 4, 5) for value in values.values()):
                raise SystemExit(f"line {line_number}: labels must be ordinal values 1..5")
            observed.update((kind, key) for key in values)
        if observed != expected[language]:
            raise SystemExit(f"line {line_number}: hypothesis coverage does not match catalog")
        rows.append(row)

    counts = Counter(row["language"].lower() for row in rows)
    for language in ("en", "pt"):
        if counts[language] < args.min_per_language:
            raise SystemExit(
                f"need at least {args.min_per_language} {language} rows; found {counts[language]}"
            )
    print(json.dumps({"rows": len(rows), "languages": dict(counts), "status": "VALID"}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
