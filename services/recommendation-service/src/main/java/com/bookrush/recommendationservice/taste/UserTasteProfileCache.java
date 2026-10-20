package com.bookrush.recommendationservice.taste;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Rebuildable, subject-scoped profile cache with explicit version and invalidation. */
public final class UserTasteProfileCache {
  private final Duration ttl;
  private final Map<CacheKey, Entry> values = new ConcurrentHashMap<>();

  public UserTasteProfileCache(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
    this.ttl = ttl;
  }

  public Optional<UserTasteProfile> get(String subject, String modelVersion, String configHash,
      Instant now) {
    if (subject == null || subject.isBlank()) return Optional.empty();
    CacheKey key = new CacheKey(subject, modelVersion, configHash);
    Entry entry = values.get(key);
    if (entry == null) return Optional.empty();
    if (!entry.expiresAt().isAfter(now)) {
      values.remove(key, entry);
      return Optional.empty();
    }
    return Optional.of(entry.profile());
  }

  public void put(String subject, UserTasteProfile profile, String modelVersion, Instant now) {
    if (subject == null || subject.isBlank() || profile == null) return;
    values.put(new CacheKey(subject, modelVersion, profile.configurationHash()),
        new Entry(profile, now.plus(ttl)));
  }

  public void invalidate(String subject) {
    if (subject == null || subject.isBlank()) return;
    values.keySet().removeIf(key -> key.subject().equals(subject));
  }

  public int size() { return values.size(); }

  private record CacheKey(String subject, String modelVersion, String configHash) { }
  private record Entry(UserTasteProfile profile, Instant expiresAt) { }
}
