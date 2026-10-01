#!/usr/bin/env python3
"""Offline deterministic harness; reports measured throughput without model downloads."""
import argparse, json, time
from pathlib import Path
def main():
 p=argparse.ArgumentParser(); p.add_argument('--input',required=True); p.add_argument('--iterations',type=int,default=1); a=p.parse_args()
 text=Path(a.input).read_text(encoding='utf-8'); started=time.perf_counter(); count=0
 for _ in range(a.iterations): count += sum(1 for x in text.splitlines() if x.strip())
 elapsed=max(time.perf_counter()-started,1e-9)
 print(json.dumps({'iterations':a.iterations,'records':count,'seconds':elapsed,'records_per_second':count/elapsed,'model_download':False}))
if __name__=='__main__': main()
