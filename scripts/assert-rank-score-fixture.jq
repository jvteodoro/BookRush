# Snapshots are minute buckets, not a single row for the whole requested period.
# Check the latest snapshot for this exact ranker configuration.
# Slurp requires exactly one response, including rejecting empty HTTP bodies.
if length != 1 then false else .[0] | (
.status == "UNTRUSTED" and (
  [.series[]? | select(
    .ranker_code == "fixture-ranker" and
    .ranker_version == "v1" and
    .configuration_hash == ("a" * 64)
  )] | sort_by(.collected_at) |
  if length == 0 then false else
    .[-1].collected_at as $latest |
    map(select(.collected_at == $latest)) |
    length == 1 and (.[0] |
      .score_decile == 0 and .excerpt_count == 1 and
      .score_min == 0.9 and .score_max == 0.9)
  end
)
) end
