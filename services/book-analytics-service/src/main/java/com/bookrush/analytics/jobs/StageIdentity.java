package com.bookrush.analytics.jobs;

import java.util.*;

/** Identity used to decide whether a cached stage output is safe to reuse. */
public record StageIdentity(UUID assetVersionId, String textSha256, String analyzerVersion, String modelRevision, String configurationHash) {
  public StageIdentity { Objects.requireNonNull(assetVersionId); Objects.requireNonNull(textSha256); Objects.requireNonNull(analyzerVersion); Objects.requireNonNull(configurationHash); }
  public boolean reusableBy(StageIdentity requested) { return equals(requested) || (assetVersionId.equals(requested.assetVersionId) && textSha256.equals(requested.textSha256) && analyzerVersion.equals(requested.analyzerVersion) && Objects.equals(modelRevision,requested.modelRevision) && configurationHash.equals(requested.configurationHash)); }
}
