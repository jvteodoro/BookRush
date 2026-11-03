package com.bookrush.analytics.worker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bookrush.analytics.storage.TextAssetReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class AnalyticsJobWorkerRetryTest {
  @Test
  void retriesTransientStorageAndTimeoutFailures() {
    assertTrue(AnalyticsJobWorker.isRetryable("SocketTimeoutException", new IOException("timeout")));
    assertTrue(AnalyticsJobWorker.isRetryable("ResourceAccessException", new RuntimeException("down")));
  }

  @Test
  void keepsIntegrityAndMissingAssetsTerminal() {
    assertFalse(
        AnalyticsJobWorker.isRetryable(
            "HASH_MISMATCH", new TextAssetReader.IntegrityException("HASH_MISMATCH")));
    assertFalse(
        AnalyticsJobWorker.isRetryable(
            "ASSET_VERSION_UNAVAILABLE", new IllegalStateException("missing")));
  }
}
