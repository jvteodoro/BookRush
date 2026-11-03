#!/usr/bin/env python3
"""Join recommendation decisions and behavior outcomes without cross-schema JDBC."""
import csv
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone


LABELS = (
    "viewable", "dwell", "open", "save", "like", "share",
    "meaningful_read", "read_progress", "read_10", "read_50", "complete",
)


def parse_time(value):
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def main():
    if len(sys.argv) != 5:
        raise SystemExit("usage: join-pfv1-dataset.py decisions.csv outcomes.csv cutoff output.csv")
    decisions_path, outcomes_path, cutoff_raw, output_path = sys.argv[1:]
    cutoff = parse_time(cutoff_raw)
    if cutoff is None:
        raise SystemExit("cutoff must be an ISO-8601 timestamp")
    with open(decisions_path, newline="") as stream:
        decisions = {row.get("recommendation_item_id"): parse_time(row.get("decision_at"))
                     for row in csv.DictReader(stream)}
    observations = defaultdict(lambda: {label: [] for label in LABELS})
    with open(outcomes_path, newline="") as stream:
        for row in csv.DictReader(stream):
            item_id = row.get("recommendation_item_id")
            occurred = parse_time(row.get("occurred_at"))
            decision = decisions.get(item_id)
            if not item_id or not occurred or not decision:
                continue
            if not decision <= occurred < min(cutoff, decision + timedelta(days=7)):
                continue
            outcome = row.get("outcome_type", "").lower()
            if outcome in ("book_open", "open"):
                label = "open"
            elif outcome in ("book_save", "save"):
                label = "save"
            elif outcome in ("book_like", "like"):
                label = "like"
            elif outcome in ("book_share", "share"):
                label = "share"
            elif outcome in ("read_progress", "progress"):
                label = "read_progress"
            elif outcome in ("book_complete", "read_complete", "complete"):
                label = "complete"
            elif outcome == "read_session":
                label = "dwell"
            else:
                continue
            observations[item_id][label].append(row)
            if label == "dwell":
                try:
                    seconds = int(row.get("active_seconds") or 0)
                    if seconds >= 60:
                        observations[item_id]["meaningful_read"].append(row)
                except ValueError:
                    pass
            if label == "read_progress":
                try:
                    percent = float(row.get("percent") or 0)
                    if percent >= 10:
                        observations[item_id]["read_10"].append(row)
                    if percent >= 50:
                        observations[item_id]["read_50"].append(row)
                except ValueError:
                    pass

    with open(decisions_path, newline="") as source, open(output_path, "w", newline="") as target:
        reader = csv.DictReader(source)
        fields = reader.fieldnames or []
        fields += [name for name in LABELS if name not in fields]
        fields += [name + "_at" for name in LABELS if name + "_at" not in fields]
        writer = csv.DictWriter(target, fieldnames=fields)
        writer.writeheader()
        for row in reader:
            item_id = row.get("recommendation_item_id", "")
            current = observations[item_id]
            for label in LABELS:
                values = current[label]
                row[label] = "1" if values or (label == "viewable" and row.get("viewed_label") == "1") else "0"
                row[label + "_at"] = min((item.get("occurred_at", "") for item in values), default="")
            writer.writerow(row)


if __name__ == "__main__":
    main()
