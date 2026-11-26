package com.bookrush.readerprofileservice.api;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessAuthorization;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@ConditionalOnProperty(name = "bookrush.security.product-access.enabled", havingValue = "true")
final class ProfileDiscovery {
  private final ProductAccessAuthorization gate;
  private final String issuer;
  private final String prefix;

  ProfileDiscovery(ProductAccessAuthorization gate, @Value("${bookrush.security.issuer}") String issuer) {
    this.gate = gate;
    this.issuer = issuer;
    this.prefix = "TechnicalIdentity[issuer=" + issuer + ", subject=";
  }

  String storedKey(String subject) {
    return new TechnicalIdentity(issuer, subject).toString();
  }

  String subject(String key) {
    if (key == null || key.length() > prefix.length() + 257) return null;
    String value = key.startsWith(prefix) && key.endsWith("]") ? key.substring(prefix.length(), key.length() - 1) : key;
    return value.matches("[a-zA-Z0-9._:@-]{1,256}") ? value : null;
  }

  Set<String> visible(Set<String> candidates) {
    var context = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return gate.discoverableProfiles(context.getRequest(), candidates);
  }
}
