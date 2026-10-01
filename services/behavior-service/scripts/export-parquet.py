#!/usr/bin/env python3
"""Reproducible behavior export. Reads a bounded cutoff and writes data + manifest."""
import argparse, hashlib, json, os, pathlib
from datetime import datetime, timezone

def main():
 p=argparse.ArgumentParser(); p.add_argument('--output',required=True); p.add_argument('--cutoff',required=True); p.add_argument('--source',default='postgresql'); a=p.parse_args()
 # Export adapter accepts JSONL from a SQL query, keeping DB credentials out of artifacts.
 rows=[]
 source=pathlib.Path(a.source)
 if source.exists(): rows=[json.loads(x) for x in source.read_text().splitlines() if x.strip()]
 out=pathlib.Path(a.output); out.parent.mkdir(parents=True,exist_ok=True)
 try:
  import pyarrow as pa, pyarrow.parquet as pq
  table=pa.Table.from_pylist(rows); pq.write_table(table,out)
 except ImportError:
  out.write_text('\n'.join(json.dumps(r,sort_keys=True) for r in rows)+'\n')
 digest=hashlib.sha256(out.read_bytes()).hexdigest(); manifest={'schemaVersion':'behavior-events-v1','cutoff':a.cutoff,'rowCount':len(rows),'artifact':out.name,'sha256':digest,'createdAt':datetime.now(timezone.utc).isoformat()}
 out.with_suffix(out.suffix+'.manifest.json').write_text(json.dumps(manifest,indent=2)+'\n'); print(json.dumps(manifest))
if __name__=='__main__': main()
