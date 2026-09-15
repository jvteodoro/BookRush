#!/usr/bin/env python3
"""Validate Content Analytics V1 contracts and cross-file identities."""
from pathlib import Path
import hashlib, json, sys
import yaml
import jsonschema

ROOT = Path(__file__).resolve().parents[1] / "services/book-analytics-service/config"
required = ["analytics-spec-v1.yaml", "model-registry-v1.yaml", "narrative-hypotheses-v1.yaml", "semantic-prototypes-v1.yaml", "analytics-spec-v1.schema.json"]
for name in required:
    path = ROOT / name
    if not path.is_file():
        raise SystemExit(f"missing contract: {path}")

data = {name: yaml.safe_load((ROOT/name).read_text()) for name in required if name.endswith('.yaml')}
schema = json.loads((ROOT/'analytics-spec-v1.schema.json').read_text())
jsonschema.validate(data['analytics-spec-v1.yaml'], schema)
if data['analytics-spec-v1.yaml'].get('spec_version') != '1.0.0':
    raise SystemExit('analytics spec version must be 1.0.0')
models = {m['id']: m for m in data['model-registry-v1.yaml'].get('models', [])}
for model_id in ('fasttext-lid176-v1','spacy-en-core-web-sm-v1','spacy-pt-core-news-sm-v1','bge-m3-dense-v1','mdeberta-nli-v1'):
    if model_id not in models: raise SystemExit(f'missing model {model_id}')
bge = models['bge-m3-dense-v1']
if bge.get('dimension') != 1024 or bge.get('mode') != 'dense' or bge.get('sparse') or bge.get('colbert'):
    raise SystemExit('BGE-M3 contract must be dense 1024 without sparse/colbert outputs')
features = data['analytics-spec-v1.yaml'].get('features', [])
codes = [f['code'] for f in features]
if len(codes) != len(set(codes)): raise SystemExit('duplicate feature code')
for f in features:
    if f.get('category') not in {'MEASUREMENT','DERIVED_MEASUREMENT','MODEL_REPRESENTATION','MODEL_SCORE','DERIVED_MODEL_SCORE','PRODUCT_SCORE'}:
        raise SystemExit(f"invalid category for {f['code']}")
print(f'validated {len(features)} feature contracts and {len(models)} model contracts')
