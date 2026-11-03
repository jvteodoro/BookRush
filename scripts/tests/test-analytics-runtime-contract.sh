#!/usr/bin/env bash
set -euo pipefail
runtime='services/book-analytics-runtime/app/main.py'
python3 - "$runtime" <<'PY'
from pathlib import Path
import ast, sys

path = Path(sys.argv[1])
tree = ast.parse(path.read_text())
source = path.read_text()
assert "@app.post('/v1/linguistic'" in source
for field in ('dependency_depth_mean', 'dependency_distance_mean', 'entity_count',
              'opening_pronoun_ratio', 'unresolved_reference_proxy',
              'backward_connective_ratio'):
    assert field in source, field
assert 'coreference resolution' in Path('services/book-analytics-runtime/README.md').read_text()
assert any(isinstance(node, ast.FunctionDef) and node.name == 'linguistic' for node in ast.walk(tree))
print('analytics runtime linguistic contract smoke passed')
PY
