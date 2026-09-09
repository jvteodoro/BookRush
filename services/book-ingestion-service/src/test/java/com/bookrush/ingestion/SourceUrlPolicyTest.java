package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.source.SourceUrlPolicy;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceUrlPolicyTest {
  @Test void rejectsCredentialsRedirectOverflowAndUntrustedHosts() {
    var policy = new SourceUrlPolicy(Set.of("example.test"), true, 2);
    assertEquals("example.test", policy.validate(URI.create("https://example.test/catalog.rdf"), 0).getHost());
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://user:pass@example.test/a"), 0));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://evil.test/a"), 0));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://example.test/a"), 3));
  }

  @Test void rejectsLoopbackWhenPrivateAddressesAreDisabled() {
    var policy = new SourceUrlPolicy(Set.of("localhost"), false, 0);
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("http://localhost:8080/fixture"), 0));
  }
}
