package com.bookrush.catalog.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class FieldPolicyTest {
  private final FieldPolicy policy = new FieldPolicy();
  @Test void curatedMetadataWinsAndMissingDoesNotErase() {
    assertThat(policy.decide(FieldPolicy.Authority.CURATED, false, FieldPolicy.Authority.SOURCE, true, false).apply()).isFalse();
    assertThat(policy.decide(FieldPolicy.Authority.SOURCE, false, FieldPolicy.Authority.SOURCE, false, false).apply()).isFalse();
  }
  @Test void sourceMetadataCanFillUnknownField() {
    assertThat(policy.decide(FieldPolicy.Authority.UNKNOWN, false, FieldPolicy.Authority.SOURCE, true, false).apply()).isTrue();
  }
}
