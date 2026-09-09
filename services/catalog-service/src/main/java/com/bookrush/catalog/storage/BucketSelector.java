package com.bookrush.catalog.storage;

import com.bookrush.catalog.persistence.model.BookAssetAssetRole;

public final class BucketSelector {
  private final StorageProperties.Buckets buckets;
  public BucketSelector(StorageProperties.Buckets buckets) { this.buckets=buckets; }
  public String select(BookAssetAssetRole role) {
    return switch (role) {
      case SOURCE -> buckets.source();
      case PUBLIC, COVER -> buckets.publicBucket();
      case ANALYTICS, ML -> buckets.ml();
      case NORMALIZED, PROCESSING, DERIVED -> buckets.processing();
    };
  }
}
