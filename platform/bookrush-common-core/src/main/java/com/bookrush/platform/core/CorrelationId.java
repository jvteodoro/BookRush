package com.bookrush.platform.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validated correlation value safe to propagate through logs and HTTP headers. */
public record CorrelationId(String value) {
  private static final int MAX_LENGTH = 128;
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

  public CorrelationId {
    Objects.requireNonNull(value, "value");
    if (value.length() > MAX_LENGTH || !SAFE.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid correlation id");
    }
  }

  public static CorrelationId parse(String value) {
    return new CorrelationId(value);
  }
}
