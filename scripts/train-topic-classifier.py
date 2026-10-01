#!/usr/bin/env python3
"""Train the reproducible metadata-supervised topic classifier.

Input JSONL rows contain work_id, embedding (1024 floats), and labels (list of
canonical subject codes). Splitting is performed by work_id, so editions and
text versions of one work cannot leak across train/test.
"""
from __future__ import annotations
import argparse, json, hashlib
from pathlib import Path
import numpy as np

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--input", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--taxonomy-version", default="book-subjects-v1")
    args = p.parse_args()
    rows = [json.loads(line) for line in Path(args.input).read_text(encoding="utf-8").splitlines() if line.strip()]
    if not rows: raise SystemExit("empty classifier dataset")
    try:
        from sklearn.linear_model import LogisticRegression
        from sklearn.multiclass import OneVsRestClassifier
        from sklearn.model_selection import GroupShuffleSplit
        from sklearn.preprocessing import MultiLabelBinarizer
        from sklearn.metrics import f1_score, precision_score, recall_score
    except ImportError as exc:
        raise SystemExit("scikit-learn is required for topic training") from exc
    groups = np.asarray([str(row["work_id"]) for row in rows])
    X = np.asarray([row["embedding"] for row in rows], dtype=np.float32)
    binarizer = MultiLabelBinarizer()
    labels = binarizer.fit_transform([row.get("labels", []) for row in rows])
    label_names = binarizer.classes_.tolist()
    splitter = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=1337)
    train, test = next(splitter.split(X, labels, groups=groups))
    model = OneVsRestClassifier(LogisticRegression(max_iter=1000, class_weight="balanced", solver="liblinear"))
    model.fit(X[train], labels[train])
    predicted = model.predict(X[test])
    metrics = {"macro_f1": float(f1_score(labels[test], predicted, average="macro", zero_division=0)),
               "micro_f1": float(f1_score(labels[test], predicted, average="micro", zero_division=0)),
               "macro_precision": float(precision_score(labels[test], predicted, average="macro", zero_division=0)),
               "macro_recall": float(recall_score(labels[test], predicted, average="macro", zero_division=0)),
               "test_rows": int(len(test)), "label_count": len(label_names)}
    payload = {"code": "BOOK_TOPIC_CLASSIFIER_V1", "taxonomy_version": args.taxonomy_version,
               "labels": label_names, "metrics": metrics, "work_split": "GroupShuffleSplit/1337",
               "dataset_sha256": hashlib.sha256(Path(args.input).read_bytes()).hexdigest(),
               "status": "EXPERIMENTAL", "model": model, "binarizer": binarizer}
    # joblib is intentionally optional; the artifact is only produced in the
    # explicit training environment, never downloaded or loaded at startup.
    import joblib
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(payload, args.output)
    print(json.dumps(metrics, sort_keys=True))

if __name__ == "__main__":
    main()
