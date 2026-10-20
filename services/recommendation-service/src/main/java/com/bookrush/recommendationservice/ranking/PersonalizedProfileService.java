package com.bookrush.recommendationservice.ranking;

import com.bookrush.recommendationservice.taste.UserTasteProfile;
import com.bookrush.recommendationservice.taste.UserTasteProfileBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Builds a bounded profile from behavior signals and versioned analytics embeddings. */
@Component
public final class PersonalizedProfileService {
  private static final UserTasteProfileBuilder.Config CONFIG =
      new UserTasteProfileBuilder.Config("taste-profile-v1", Duration.ofDays(30), 50, 4, 3);
  private final BehaviorSignalsClient behavior;
  private final AnalyticsScoreClient analytics;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  public PersonalizedProfileService(BehaviorSignalsClient behavior, AnalyticsScoreClient analytics) {
    this.behavior = behavior;
    this.analytics = analytics;
  }

  public Result load(String subject) {
    Instant now = Instant.now();
    var existing = cache.get(subject);
    if (existing != null && existing.expiresAt().isAfter(now)) return existing.result();
    var signals = behavior.find(subject);
    if (signals.isEmpty()) {
      var result = new Result(null, false, false, List.of(), Map.of());
      cache.put(subject, new Entry(result, now.plus(Duration.ofMinutes(2))));
      return result;
    }
    var ids = signals.stream().map(BehaviorSignalsClient.Signal::bookId).distinct().toList();
    var snapshots = analytics.findSnapshots(ids, true);
    var embeddings = snapshots.values().stream()
        .filter(snapshot -> snapshot.embedding() != null && snapshot.embedding().vector() != null)
        .collect(java.util.stream.Collectors.toUnmodifiableMap(
            AnalyticsScoreClient.BookFeatureSnapshot::bookId,
            snapshot -> new UserTasteProfileBuilder.BookEmbedding(snapshot.bookId(),
                snapshot.embedding().model(), snapshot.embedding().modelVersion(),
                snapshot.embedding().sourceAssetVersionId(), snapshot.embedding().inputSha256(),
                snapshot.embedding().vector()),
            (left, right) -> left));
    var builderSignals = signals.stream().map(signal -> new UserTasteProfileBuilder.Signal(
        signal.bookId(), signal.impressionCount(), signal.openCount(), signal.completionCount(),
        signal.activeSeconds(), signal.maxProgress(), signal.liked(), signal.saved(), signal.lastEventAt())).toList();
    var profile = new UserTasteProfileBuilder(CONFIG).build(builderSignals, embeddings, now);
    var result = new Result(profile, true, !embeddings.isEmpty(), signals, snapshots);
    cache.put(subject, new Entry(result, now.plus(Duration.ofMinutes(2))));
    return result;
  }

  public record Result(UserTasteProfile profile, boolean behaviorAvailable,
      boolean snapshotAvailable, List<BehaviorSignalsClient.Signal> signals,
      Map<UUID, AnalyticsScoreClient.BookFeatureSnapshot> snapshots) {
    public Result {
      signals = signals == null ? List.of() : List.copyOf(signals);
      snapshots = snapshots == null ? Map.of() : Map.copyOf(snapshots);
    }
  }

  private record Entry(Result result, Instant expiresAt) { }
}
