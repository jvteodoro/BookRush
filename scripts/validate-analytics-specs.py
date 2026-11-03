#!/usr/bin/env python3
"""Validate Content Analytics V1 contracts and cross-file identities."""
from pathlib import Path
import hashlib, json, sys
import yaml
import jsonschema

ROOT = Path(__file__).resolve().parents[1] / "services/book-analytics-service/config"
required = ["analytics-spec-v1.yaml", "model-registry-v1.yaml", "narrative-hypotheses-v1.yaml", "semantic-prototypes-v1.yaml", "semantic-prototypes-v2.yaml", "analytics-spec-v1.schema.json", "feature-catalog-v2.yaml", "model-artifacts-v1.json"]
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
prototype_artifact_path = ROOT / 'semantic-prototypes-v1.artifact.json'
if not prototype_artifact_path.is_file():
    raise SystemExit('materialized semantic prototype artifact is missing')
prototype_artifact = json.loads(prototype_artifact_path.read_text())
artifact_checksum = prototype_artifact.pop('artifact_sha256', '')
canonical = json.dumps(prototype_artifact, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()
if len(artifact_checksum) != 64 or hashlib.sha256(canonical).hexdigest() != artifact_checksum:
    raise SystemExit('semantic prototype artifact checksum is invalid')
if (prototype_artifact.get('artifact_type') != 'bookrush.semantic-prototype-set'
        or prototype_artifact.get('embedding_model') != 'bge-m3-dense-v1'
        or prototype_artifact.get('dimension') != 1024
        or len(prototype_artifact.get('examples', [])) != 72
        or len(prototype_artifact.get('prototypes', [])) != 36):
    raise SystemExit('semantic prototype artifact contract is incomplete')
for prototype in prototype_artifact['prototypes']:
    norm = sum(float(value) ** 2 for value in prototype['vector'])
    if len(prototype['vector']) != 1024 or abs(norm - 1.0) > 1e-5:
        raise SystemExit('semantic prototype vectors must be normalized 1024-dimensional vectors')
artifact_lock = json.loads((ROOT/'model-artifacts-v1.json').read_text())
if artifact_lock.get('schemaVersion') != 'bookrush.analytics.model-artifacts.v1':
    raise SystemExit('unsupported model artifact lock schema')
locked = {artifact['id']: artifact for artifact in artifact_lock.get('artifacts', [])}
for model_id, model in models.items():
    if model_id not in locked:
        raise SystemExit(f'model artifact lock missing {model_id}')
    artifact = locked[model_id]
    files = artifact.get('files', [])
    if not files or any(len(item.get('sha256', '')) != 64 for item in files):
        raise SystemExit(f'model artifact lock has incomplete checksums for {model_id}')
    source = artifact.get('source', {})
    if source.get('type') == 'huggingface':
        if model.get('revision') != source.get('revision') or len(source.get('revision', '')) != 40:
            raise SystemExit(f'Hugging Face revision must be immutable and match lock for {model_id}')
    if model.get('license') != artifact.get('license'):
        raise SystemExit(f'license mismatch for {model_id}')
features = data['analytics-spec-v1.yaml'].get('features', [])
codes = [f['code'] for f in features]
if len(codes) != len(set(codes)): raise SystemExit('duplicate feature code')
for f in features:
    if f.get('category') not in {'MEASUREMENT','DERIVED_MEASUREMENT','MODEL_REPRESENTATION','MODEL_SCORE','DERIVED_MODEL_SCORE','PRODUCT_SCORE'}:
        raise SystemExit(f"invalid category for {f['code']}")
v2 = data['feature-catalog-v2.yaml']
if v2.get('version') != 'analytics-feature-catalog-v2.0.0':
    raise SystemExit('analytics V2 feature catalog version must be analytics-feature-catalog-v2.0.0')
v2_features = v2.get('features', [])
v2_codes = [feature.get('code') for feature in v2_features]
if not v2_features or any(not code for code in v2_codes) or len(v2_codes) != len(set(v2_codes)):
    raise SystemExit('V2 feature catalog must have unique non-empty codes')
for feature in v2_features:
    if feature.get('layer') not in {'MEASUREMENT', 'MODEL_INFERENCE', 'PRODUCT_SCORE'}:
        raise SystemExit(f"invalid V2 layer for {feature['code']}")
    if not isinstance(feature.get('tier'), int) or feature['tier'] < 0 or feature['tier'] > 3:
        raise SystemExit(f"invalid V2 tier for {feature['code']}")
    if feature.get('layer') == 'MEASUREMENT' and (not isinstance(feature.get('languages'), list) or not feature['languages']):
        raise SystemExit(f"missing V2 language policy for {feature['code']}")
prototype_v2 = data['semantic-prototypes-v2.yaml']
if prototype_v2.get('version') != 'semantic-prototype-set-v2.0.0' or prototype_v2.get('embedding_model') != 'bge-m3-dense-v1':
    raise SystemExit('invalid V2 semantic prototype contract')
if set(prototype_v2.get('concepts', {})) != {'action', 'dialogue', 'description', 'reflection', 'conflict', 'mystery'}:
    raise SystemExit('V2 semantic prototype concepts are incomplete')
narrative = data['narrative-hypotheses-v1.yaml']
required_narrative = {'conflict', 'suspense', 'introspection', 'self_containment',
                      'curiosity_gap', 'cliffhanger', 'revelation', 'resolution', 'quotability'}
if narrative.get('model') != 'mdeberta-nli-v1' or set(narrative.get('narrative', {})) != required_narrative:
    raise SystemExit('narrative hypothesis contract is incomplete')
if set(narrative.get('emotions', {})) != {'joy', 'sadness', 'fear', 'anger', 'surprise', 'disgust', 'affection'}:
    raise SystemExit('emotion hypothesis contract is incomplete')
validation = narrative.get('validation', {})
if validation.get('sample_en') != 50 or validation.get('sample_pt') != 50:
    raise SystemExit('NLI validation sample must be 50 per language')
print(f'validated {len(features)} V1 feature contracts, {len(v2_features)} V2 feature contracts and {len(models)} locked model contracts')
