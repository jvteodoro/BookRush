#!/usr/bin/env python3
"""Export a deterministic, cutoff-bounded behavior JSONL snapshot to Parquet."""
import argparse, hashlib, json, pathlib
from datetime import datetime, timezone

SCHEMA = {
    "eventKey": "string", "eventType": "string", "identityIssuer": "string",
    "identitySubject": "string", "bookId": "string", "payload": "string",
    "occurredAt": "timestamp[us, tz=UTC]", "receivedAt": "timestamp[us, tz=UTC]",
}

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--cutoff', required=True)
    parser.add_argument('--source', required=True, help='JSONL snapshot produced by a read-only SQL export')
    args = parser.parse_args()
    cutoff = datetime.fromisoformat(args.cutoff.replace('Z', '+00:00')).astimezone(timezone.utc)
    rows = []
    for line in pathlib.Path(args.source).read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        occurred = datetime.fromisoformat(str(row['occurredAt']).replace('Z', '+00:00')).astimezone(timezone.utc)
        if occurred <= cutoff:
            rows.append({
                'eventKey': str(row['eventKey']), 'eventType': str(row['eventType']),
                'identityIssuer': row.get('identityIssuer'), 'identitySubject': row.get('identitySubject'),
                'bookId': row.get('bookId'), 'payload': json.dumps(row.get('payload') or {}, sort_keys=True, separators=(',', ':')),
                'occurredAt': occurred, 'receivedAt': datetime.fromisoformat(str(row.get('receivedAt', row['occurredAt'])).replace('Z', '+00:00')).astimezone(timezone.utc),
            })
    rows.sort(key=lambda row: (row['occurredAt'], row['eventKey']))
    try:
        import pyarrow as pa
        import pyarrow.parquet as pq
    except ImportError as exc:
        raise SystemExit('pyarrow is required; install services/behavior-service/scripts/requirements-export.txt') from exc
    table = pa.Table.from_pylist(rows)
    output = pathlib.Path(args.output); output.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(table, output, compression='zstd', coerce_timestamps='us')
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    manifest = {'schemaVersion': 'behavior-events-v1', 'columns': SCHEMA, 'cutoff': cutoff.isoformat(), 'rowCount': len(rows), 'artifact': output.name, 'sha256': digest}
    output.with_suffix(output.suffix + '.manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest, sort_keys=True))

if __name__ == '__main__':
    main()
