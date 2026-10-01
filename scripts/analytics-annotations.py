#!/usr/bin/env python3
"""Validate and filter H/C/A/I/Q JSONL without exposing full-book text."""
import argparse, json, sys
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT=Path(__file__).parents[1]
SCHEMA=ROOT/'services/book-analytics-service/config/annotations/hcaiq-annotation-v1.schema.json'
VALIDATOR=Draft202012Validator(json.loads(SCHEMA.read_text()))

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    args=parser.parse_args()
    accepted=0
    with args.input.open() as source, args.output.open('w') as target:
        for line_no,line in enumerate(source,1):
            if not line.strip(): continue
            row=json.loads(line)
            errors=list(VALIDATOR.iter_errors(row))
            if errors:
                raise SystemExit(f'{args.input}:{line_no}: '+ '; '.join(e.message for e in errors))
            target.write(json.dumps(row,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n')
            accepted+=1
    print(f'accepted {accepted} annotation rows')

if __name__=='__main__': main()
