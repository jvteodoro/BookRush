#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
check() {
  local expected="$1" payload="$2" actual=0
  jq -e -s -f "$repo_dir/scripts/assert-rank-score-fixture.jq" <<<"$payload" >/dev/null || actual=$?
  [[ "$actual" == "$expected" ]] || {
    echo "Rank score fixture regression: expected=$expected actual=$actual" >&2
    exit 1
  }
}
one="$(jq -n '{status:"UNTRUSTED",series:[{
  collected_at:"2026-11-24T15:20:00Z", ranker_code:"fixture-ranker",
  ranker_version:"v1", configuration_hash:("a" * 64), score_decile:0,
  score_min:0.9, score_max:0.9, excerpt_count:1
}]}')"
two="$(jq '.series += [.series[0] | .collected_at="2026-11-24T15:21:00Z"] | .series |= reverse' <<<"$one")"
check 1 ""
check 1 "null"
check 1 "$one $one"
check 0 "$one"
check 0 "$two"
# An old correct bucket must never hide an incorrect current bucket.
for field in score_min score_max excerpt_count score_decile; do
  check 1 "$(jq --arg field "$field" '.series[0][$field]=7' <<<"$two")"
done
# Old values are historical; only the latest bucket describes the current fixture.
check 0 "$(jq '.series[1].score_min=0.2' <<<"$two")"
check 1 "$(jq '.series=[]' <<<"$one")"
check 1 "$(jq '.series += [.series[0] | .score_decile=1]' <<<"$one")"
check 1 "$(jq '.status="TRUSTED"' <<<"$one")"
check 1 "$(jq '.series[0].ranker_version="other"' <<<"$one")"
check 1 "$(jq '.series[0].configuration_hash=("b" * 64)' <<<"$one")"
check 0 "$(jq '.series += [.series[0] | .configuration_hash=("b" * 64) | .score_min=0.2]' <<<"$one")"
echo 'Rank score fixture: single/multiple minute buckets, latest values and configuration isolation OK'
