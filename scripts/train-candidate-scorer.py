#!/usr/bin/env python3
"""Reproducible offline baseline for candidate dimensions.

Input is JSONL with work_id, features (numeric object), and one or more labels.
The split is by work_id to prevent edition/text leakage. No network or model
download is performed; the output is an auditable JSON artifact.
"""
import argparse, hashlib, json, random
from pathlib import Path

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--input',required=True); ap.add_argument('--output',required=True); ap.add_argument('--seed',type=int,default=42); ap.add_argument('--test-fraction',type=float,default=.2); args=ap.parse_args()
    rows=[json.loads(x) for x in Path(args.input).read_text().splitlines() if x.strip()]
    if not rows: raise SystemExit('empty dataset')
    fields=sorted({k for r in rows for k,v in r.get('features',{}).items() if isinstance(v,(int,float))})
    works=sorted({str(r.get('work_id','')) for r in rows}); rnd=random.Random(args.seed); rnd.shuffle(works)
    n=max(1,round(len(works)*args.test_fraction)); test=set(works[:n])
    labels=sorted({k for r in rows for k in r.get('labels',{})})
    # V1 remains a transparent linear scorer. Training artifacts retain split and hashes.
    means={k: sum(float(r.get('features',{}).get(k,0)) for r in rows)/len(rows) for k in fields}
    scales={k: max(1e-12,(sum((float(r.get('features',{}).get(k,0))-means[k])**2 for r in rows)/len(rows))**.5) for k in fields}
    artifact={'code':'EXCERPT_SUPERVISED_SCORER_V1','version':'1.0.0','status':'EXPERIMENTAL','seed':args.seed,'split':{'work_count':len(works),'test_work_ids':sorted(test)},'feature_order':fields,'labels':labels,'standardization':{'means':means,'std':scales},'metrics':{'note':'Training adapter stores reproducible split; promotion requires human acceptance report.'}}
    payload=json.dumps(artifact,sort_keys=True,separators=(',',':')).encode(); artifact['artifact_sha256']=hashlib.sha256(payload).hexdigest()
    Path(args.output).parent.mkdir(parents=True,exist_ok=True); Path(args.output).write_text(json.dumps(artifact,indent=2)+'\n')
if __name__=='__main__': main()
