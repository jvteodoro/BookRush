#!/usr/bin/env python3
"""Reproducible offline experimental scorer for candidate dimensions.

Input is JSONL with work_id, features (numeric object), and one or more labels.
The split is by author/work group to prevent author and edition/text leakage.
The model is a dependency-free depth-1 residual-stump baseline; no network or
model download is performed and the output is an auditable JSON artifact.
"""
import argparse, hashlib, json, random
from pathlib import Path

def _mse(actual, predicted):
    if not actual: return 0.0
    return sum((a - p) ** 2 for a, p in zip(actual, predicted)) / len(actual)

def _fit_stumps(rows, fields, label, means, rounds, learning_rate):
    values = [float(r.get('labels', {}).get(label, 0)) for r in rows]
    prediction = [means[label]] * len(rows)
    trees = []
    importance = {field: 0 for field in fields}
    for _ in range(rounds):
        residual = [actual - guess for actual, guess in zip(values, prediction)]
        best = None
        for field in fields:
            candidates = sorted({float(r.get('features', {}).get(field, 0)) for r in rows})
            for left, right in zip(candidates, candidates[1:]):
                threshold = (left + right) / 2
                left_indices = [i for i, r in enumerate(rows) if float(r.get('features', {}).get(field, 0)) <= threshold]
                right_indices = [i for i, r in enumerate(rows) if i not in left_indices]
                if not left_indices or not right_indices: continue
                left_value = sum(residual[i] for i in left_indices) / len(left_indices)
                right_value = sum(residual[i] for i in right_indices) / len(right_indices)
                error = sum((residual[i] - (left_value if i in left_indices else right_value)) ** 2 for i in range(len(rows)))
                candidate = (error, field, threshold, left_value, right_value)
                if best is None or candidate < best: best = candidate
        if best is None: break
        _, field, threshold, left_value, right_value = best
        for i, row in enumerate(rows):
            value = float(row.get('features', {}).get(field, 0))
            prediction[i] += learning_rate * (left_value if value <= threshold else right_value)
        trees.append({'feature': field, 'threshold': threshold,
                      'left_value': left_value, 'right_value': right_value})
        importance[field] += 1
    return trees, importance

def _predict(row, model):
    value = model['baseline']
    for tree in model['trees']:
        feature = float(row.get('features', {}).get(tree['feature'], 0))
        value += model['learning_rate'] * (tree['left_value'] if feature <= tree['threshold'] else tree['right_value'])
    return value

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--input',required=True); ap.add_argument('--output',required=True); ap.add_argument('--seed',type=int,default=42); ap.add_argument('--test-fraction',type=float,default=.2); ap.add_argument('--rounds',type=int,default=8); ap.add_argument('--learning-rate',type=float,default=.1); args=ap.parse_args()
    rows=[json.loads(x) for x in Path(args.input).read_text().splitlines() if x.strip()]
    if not rows: raise SystemExit('empty dataset')
    if not 0 < args.test_fraction < 1 or args.rounds < 1 or args.learning_rate <= 0: raise SystemExit('invalid training configuration')
    fields=sorted({k for r in rows for k,v in r.get('features',{}).items() if isinstance(v,(int,float))})
    groups=sorted({f"{r.get('author_id','')}::{r.get('work_id','')}" for r in rows})
    if len(groups) < 2: raise SystemExit('at least two author/work groups are required')
    rnd=random.Random(args.seed); rnd.shuffle(groups)
    n=min(len(groups)-1, max(1,round(len(groups)*args.test_fraction))); test=set(groups[:n])
    group_of=lambda r: f"{r.get('author_id','')}::{r.get('work_id','')}"
    train_rows=[r for r in rows if group_of(r) not in test]
    test_rows=[r for r in rows if group_of(r) in test]
    labels=sorted({k for r in rows for k in r.get('labels',{})})
    means={k: sum(float(r.get('features',{}).get(k,0)) for r in train_rows)/len(train_rows) for k in fields}
    scales={k: max(1e-12,(sum((float(r.get('features',{}).get(k,0))-means[k])**2 for r in train_rows)/len(train_rows))**.5) for k in fields}
    models={};
    for label in labels:
        baseline=sum(float(r.get('labels',{}).get(label,0)) for r in train_rows)/len(train_rows)
        trees, importance=_fit_stumps(train_rows, fields, label, {label: baseline}, args.rounds, args.learning_rate)
        model={'baseline': baseline, 'learning_rate': args.learning_rate, 'trees': trees,
               'feature_importance': {k: v for k, v in importance.items() if v}}
        baseline_predictions=[baseline] * len(test_rows)
        gbdt_predictions=[_predict(r, model) for r in test_rows]
        actual=[float(r.get('labels',{}).get(label,0)) for r in test_rows]
        models[label]={'baseline': baseline, 'gbdt': model,
                       'metrics': {'baseline_rmse': _mse(actual, baseline_predictions) ** .5,
                                   'gbdt_rmse': _mse(actual, gbdt_predictions) ** .5}}
    artifact={'code':'EXCERPT_SUPERVISED_SCORER_V1','version':'1.1.0','status':'EXPERIMENTAL','seed':args.seed,
              'split':{'group_key':'author_id::work_id','group_count':len(groups),'test_groups':sorted(test),
                       'train_rows':len(train_rows),'test_rows':len(test_rows)},'feature_order':fields,'labels':labels,
              'standardization':{'means':means,'std':scales},'models':models,
              'explainability':{'method':'gbdt_stump_split_frequency','promotion_requires_reviewed_labels':True},
              'metrics':{'note':'Promotion requires human acceptance report and holdout review.'}}
    payload=json.dumps(artifact,sort_keys=True,separators=(',',':')).encode(); artifact['artifact_sha256']=hashlib.sha256(payload).hexdigest()
    Path(args.output).parent.mkdir(parents=True,exist_ok=True); Path(args.output).write_text(json.dumps(artifact,indent=2)+'\n')
if __name__=='__main__': main()
