#!/usr/bin/env python3
"""Build deterministic PT/EN unigram artifact from an explicit text manifest."""
import argparse, hashlib, json, re
from pathlib import Path
TOKEN=re.compile(r"[\w]+(?:['’.-][\w]+)*", re.UNICODE)
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--language',choices=['en','pt'],required=True); ap.add_argument('--snapshot',required=True); ap.add_argument('--manifest',required=True); ap.add_argument('--output',required=True); ap.add_argument('--alpha',type=float,default=.1); a=ap.parse_args()
 files=[Path(x.strip()) for x in Path(a.manifest).read_text().splitlines() if x.strip()]; counts={}; total=0
 for f in files:
  for token in TOKEN.findall(f.read_text(encoding='utf-8').casefold()): counts[token]=counts.get(token,0)+1; total+=1
 if not total: raise SystemExit('eligible corpus is empty')
 artifact={'language':a.language,'corpus_snapshot':a.snapshot,'tokenizer_version':'unicode-word-nfc-lower-v1','smoothing_alpha':a.alpha,'token_count':total,'vocabulary_size':len(counts),'counts':dict(sorted(counts.items()))}
 raw=json.dumps(artifact,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode(); artifact['artifact_sha256']=hashlib.sha256(raw).hexdigest()
 Path(a.output).parent.mkdir(parents=True,exist_ok=True); Path(a.output).write_text(json.dumps(artifact,ensure_ascii=False,indent=2)+'\n')
if __name__=='__main__': main()
