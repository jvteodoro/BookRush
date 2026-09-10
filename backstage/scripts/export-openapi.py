"""Export Springdoc from a running instance of the revision being reviewed."""
import argparse
import json
from pathlib import Path
import urllib.request
import yaml
p = argparse.ArgumentParser()
p.add_argument('--url', required=True)
p.add_argument('--output', required=True)
a = p.parse_args()
with urllib.request.urlopen(a.url, timeout=30) as response:
    document = json.load(response)
if not document.get('openapi') or not document.get('paths'):
    raise SystemExit('Not an OpenAPI document with paths')
Path(a.output).write_text(yaml.safe_dump(document, sort_keys=False, allow_unicode=True))
print('Exported contract; run catalog validation and review the diff')
