package com.bookrush.analytics.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Experimental switches for the internal book-scores query. Defaults preserve production behavior. */
@ConfigurationProperties(prefix = "analytics.book-scores")
public record BookScoresQueryProperties(String strategy, String jit) {
  public BookScoresQueryProperties {
    strategy = strategy == null || strategy.isBlank() ? "CURRENT" : strategy.toUpperCase();
    jit = jit == null || jit.isBlank() ? "DEFAULT" : jit.toUpperCase();
    if (!strategy.equals("CURRENT") && !strategy.equals("OPTIMIZED")) {
      throw new IllegalArgumentException("analytics.book-scores.strategy must be CURRENT or OPTIMIZED");
    }
    if (!jit.equals("DEFAULT") && !jit.equals("ON") && !jit.equals("OFF")) {
      throw new IllegalArgumentException("analytics.book-scores.jit must be DEFAULT, ON or OFF");
    }
  }
}
