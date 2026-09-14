package com.bookrush.platform.core;

import java.util.Objects;

/** Stable technical identity. The subject is deliberately opaque. */
public record TechnicalIdentity(String issuer, String subject) {
  public TechnicalIdentity {
    issuer = requireNonBlank(issuer, "issuer");
    subject = requireNonBlank(subject, "subject");
  }

  private static String requireNonBlank(String value, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
