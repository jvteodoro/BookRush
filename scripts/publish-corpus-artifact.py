#!/usr/bin/env python3
"""Build and publish a corpus-frequency artifact to the private ML bucket.

This command is explicit and offline-friendly: it reads only the manifest,
uploads through the configured S3-compatible endpoint, and prints metadata
needed for the analytics.corpus_frequency_model row. It never writes secrets.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re
from pathlib import Path
TOKEN = re.compile(r"[\w]+(?:['’.-][\w]+)*", re.UNICODE)

def main() -> None:
    p=argparse.ArgumentParser(); p.add_argument('--language',required=True); p.add_argument('--snapshot',required=True)
    p.add_argument('--manifest',required=True); p.add_argument('--bucket',default=os.getenv('STORAGE_BUCKET_ML','books-ml'))
    p.add_argument('--endpoint',default=os.getenv('STORAGE_ENDPOINT','http://seaweedfs:8333')); p.add_argument('--alpha',type=float,default=.1)
    args=p.parse_args()
    counts={}; total=0
    for line in Path(args.manifest).read_text(encoding='utf-8').splitlines():
        if not line.strip(): continue
        for token in TOKEN.findall(Path(line).read_text(encoding='utf-8').casefold()): counts[token]=counts.get(token,0)+1; total+=1
    if total == 0: raise SystemExit('eligible corpus is empty')
    payload={'language':args.language,'corpus_snapshot':args.snapshot,'tokenizer_version':'unicode-word-nfc-lower-v1',
             'smoothing_alpha':args.alpha,'token_count':total,'vocabulary_size':len(counts),'counts':dict(sorted(counts.items()))}
    canonical=json.dumps(payload,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
    digest=hashlib.sha256(canonical).hexdigest(); payload['artifact_sha256']=digest
    raw=(json.dumps(payload,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n').encode()
    key=f'analytics/corpus-frequency/{args.language}/{args.snapshot}-{digest}.json'
    import boto3
    client=boto3.client('s3',endpoint_url=args.endpoint,aws_access_key_id=os.environ['STORAGE_ACCESS_KEY'],aws_secret_access_key=os.environ['STORAGE_SECRET_KEY'],region_name=os.getenv('STORAGE_REGION','us-east-1'))
    client.put_object(Bucket=args.bucket,Key=key,Body=raw,ContentType='application/json')
    print(json.dumps({'language':args.language,'snapshot':args.snapshot,'tokenizer_version':payload['tokenizer_version'],
                      'vocabulary_size':len(counts),'token_count':total,'smoothing_alpha':args.alpha,
                      'artifact_sha256':digest,'bucket':args.bucket,'object_key':key},sort_keys=True))
if __name__=='__main__': main()
