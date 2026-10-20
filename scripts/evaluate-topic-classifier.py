#!/usr/bin/env python3
"""Evaluate a topic artifact without promoting it.

The input is JSONL with work_id, embedding and labels.  The artifact is the
output of train-topic-classifier.py.  Metrics are explicit about support and
missing labels; a small or single-class holdout is a failed evaluation, not a
reason to fabricate a score.
"""
from __future__ import annotations
import argparse, json, math
from pathlib import Path

import numpy as np

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--artifact", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    rows = [json.loads(line) for line in Path(args.input).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not rows:
        raise SystemExit("empty classifier dataset")
    import joblib
    from sklearn.metrics import average_precision_score, brier_score_loss, f1_score, precision_recall_fscore_support
    payload = joblib.load(args.artifact)
    model = payload["model"]
    binarizer = payload["binarizer"]
    labels = binarizer.transform([row.get("labels", []) for row in rows])
    scores = model.predict_proba(np.asarray([row["embedding"] for row in rows], dtype=np.float32))
    predicted = (scores >= float(payload.get("decision_threshold", 0.5))).astype(int)
    precision, recall, f1, support = precision_recall_fscore_support(
        labels, predicted, average=None, zero_division=0)
    per_label = {}
    for index, code in enumerate(binarizer.classes_):
        if int(support[index]) == 0:
            raise SystemExit(f"evaluation holdout has no positive support for {code}")
        per_label[code] = {
            "precision": float(precision[index]),
            "recall": float(recall[index]),
            "f1": float(f1[index]),
            "support": int(support[index]),
            "pr_auc": float(average_precision_score(labels[:, index], scores[:, index])),
            "brier": float(brier_score_loss(labels[:, index], scores[:, index]))
        }
    metrics = {
        "macro_f1": float(f1_score(labels, predicted, average="macro", zero_division=0)),
        "micro_f1": float(f1_score(labels, predicted, average="micro", zero_division=0)),
        "labels": per_label,
        "rows": len(rows),
        "artifact_status": payload.get("status", "UNKNOWN"),
        "promotion": "BLOCKED" if payload.get("status") != "ACCEPTED" else "REVIEW_REQUIRED"
    }
    Path(args.output).write_text(json.dumps(metrics, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(metrics, sort_keys=True))

if __name__ == "__main__":
    main()
