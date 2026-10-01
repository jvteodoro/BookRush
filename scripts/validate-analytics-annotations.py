#!/usr/bin/env python3
import json, sys
from pathlib import Path
from jsonschema import Draft202012Validator

schema_path = Path(__file__).parents[1] / 'services/book-analytics-service/config/annotations/hcaiq-annotation-v1.schema.json'
validator = Draft202012Validator(json.loads(schema_path.read_text()))
errors = []
for filename in sys.argv[1:]:
    for line_no, line in enumerate(Path(filename).read_text().splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            errors.append(f'{filename}:{line_no}: {exc}')
            continue
        errors.extend(f'{filename}:{line_no}: {error.message}' for error in validator.iter_errors(row))
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
print('analytics annotation rows valid')
