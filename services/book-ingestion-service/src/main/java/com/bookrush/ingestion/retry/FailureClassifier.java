package com.bookrush.ingestion.retry;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Locale;

/** Shared classification policy; callers persist the resulting state and next attempt. */
public final class FailureClassifier {
  private FailureClassifier() {}

  public static FailureClass http(int status) {
    if (status == 401 || status == 403) return FailureClass.PAUSED_AUTHORIZATION;
    if (status == 404 || status == 410) return FailureClass.OBSERVED_ABSENCE;
    if (status == 408 || status == 429 || status >= 500) return FailureClass.RETRYABLE_IO;
    if (status == 409) return FailureClass.REVIEW_REQUIRED;
    if (status >= 400) return FailureClass.QUARANTINED_INPUT;
    return FailureClass.FATAL_BUG;
  }

  public static FailureClass exception(Throwable error) {
    var name = error.getClass().getName().toLowerCase(Locale.ROOT);
    var message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
    if (error instanceof IOException || error instanceof ConnectException || name.contains("timeout")) {
      return FailureClass.RETRYABLE_IO;
    }
    if (message.contains("deadlock") || message.contains("serialization")) {
      return FailureClass.TRANSIENT_DATABASE;
    }
    if (message.contains("no space") || message.contains("disk full")) {
      return FailureClass.PAUSED_RESOURCE;
    }
    if (message.contains("checksum") || message.contains("malformed") || message.contains("zip")) {
      return FailureClass.QUARANTINED_INPUT;
    }
    return FailureClass.FATAL_BUG;
  }
}
