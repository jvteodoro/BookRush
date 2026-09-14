package com.bookrush.examples;

import com.bookrush.platform.core.TechnicalIdentity;

public final class Consumer {
  private Consumer() {}
  public static TechnicalIdentity identity(String issuer, String subject) {
    return new TechnicalIdentity(issuer, subject);
  }
}
