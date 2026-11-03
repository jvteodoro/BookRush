package com.bookrush.recommendationservice.ranking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.HexFormat;

/** Pure, deterministic summary of a personalized decision for diagnostics/replay. */
public final class PersonalizedDecisionReplay {
  public Result replay(List<PersonalizedRankerV1.ScoredCandidate> ranked,
      String profileVersion, String candidateSetHash, String configurationHash) {
    var ids = ranked == null ? List.<String>of() : ranked.stream()
        .filter(Objects::nonNull)
        .map(candidate -> candidate.candidate().id())
        .toList();
    return replayIds(ids, profileVersion, candidateSetHash, configurationHash);
  }

  public Result replayIds(List<String> orderedBookIds, String profileVersion,
      String candidateSetHash, String configurationHash) {
    var ids = orderedBookIds == null ? List.<String>of() : orderedBookIds.stream()
        .filter(Objects::nonNull).toList();
    return new Result(ids, hash(String.join("|", ids)),
        profileVersion == null ? "" : profileVersion,
        candidateSetHash == null ? "" : candidateSetHash,
        configurationHash == null ? "" : configurationHash);
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is required for personalized replay", exception);
    }
  }

  public record Result(List<String> orderedBookIds, String orderHash,
      String profileVersion, String candidateSetHash, String configurationHash) {
    public Result {
      orderedBookIds = orderedBookIds == null ? List.of() : List.copyOf(orderedBookIds);
    }
  }
}
