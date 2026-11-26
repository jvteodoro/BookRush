package com.bookrush.platform.security;

import com.bookrush.platform.core.TechnicalIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact Spring MVC method and handler-pattern classification for an opted-in application. */
public final class ProductAccessRoutes {
  public enum Action {
    SESSION_VIEW, INVITE_SELF, PRODUCT_USE, PUBLISH_READ, PUBLISH_WRITE, PUBLISH_WITHDRAW,
    FAMILY_MANAGE, SUPERVISE_HISTORY, SUPERVISE_COMMENTS
  }

  public enum Type {
    SESSION, INVITATION, CATALOG, BOOK, ASSET, FEED, PROFILE, READER_STATE, COMMENT,
    SUBMISSION, FAMILY, READING_HISTORY, TELEMETRY
  }

  public record Resource(Type type, String id, TechnicalIdentity owner) {
    public Resource {
      Objects.requireNonNull(type);
      if (id != null) checkText(id);
      if (owner != null) {
        checkText(owner.issuer());
        checkText(owner.subject());
      }
    }
  }

  @FunctionalInterface
  public interface ResourceResolver {
    /** Retrieve authoritative ownership. Throw an HTTP 404 ResponseStatusException to hide resources. */
    Resource resolve(HttpServletRequest request, TechnicalIdentity actor);
  }

  public record Policy(String issuer, String audience, Set<String> humanClients, Set<String> requiredScopes,
                       Action action, Type type, boolean ownerRequired, ResourceResolver resolver) {
    public Policy {
      checkText(issuer);
      checkText(audience);
      humanClients = Set.copyOf(humanClients);
      requiredScopes = Set.copyOf(requiredScopes);
      if (humanClients.isEmpty()) throw new IllegalArgumentException("Human client allowlist is required");
      humanClients.forEach(ProductAccessRoutes::checkText);
      requiredScopes.forEach(ProductAccessRoutes::checkText);
      Objects.requireNonNull(action);
      Objects.requireNonNull(type);
      Objects.requireNonNull(resolver);
    }
  }

  private record Key(String method, String pattern) {}
  record Rule(Policy policy) {}

  private final Map<Key, Rule> rules;

  private ProductAccessRoutes(Map<Key, Rule> rules) {
    this.rules = Map.copyOf(rules);
    if (rules.values().stream().noneMatch(rule -> rule.policy() != null)) {
      throw new IllegalArgumentException("At least one protected product route is required");
    }
  }

  Rule rule(String method, String pattern) {
    Rule rule = rules.get(new Key(method, pattern));
    return rule == null && method.equals("HEAD") ? rules.get(new Key("GET", pattern)) : rule;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final Map<Key, Rule> rules = new LinkedHashMap<>();

    public Builder protect(String method, String handlerPattern, Policy policy) {
      return add(method, handlerPattern, new Rule(Objects.requireNonNull(policy)));
    }

    /** Keep the existing Spring Security policy for this exact operation. */
    public Builder existingSecurity(String method, String handlerPattern) {
      return add(method, handlerPattern, new Rule(null));
    }

    public ProductAccessRoutes build() {
      return new ProductAccessRoutes(rules);
    }

    private Builder add(String method, String pattern, Rule rule) {
      if (method == null || !method.matches("[A-Z]+") || pattern == null || !pattern.startsWith("/")
          || pattern.replaceFirst("/\\{\\*[A-Za-z][A-Za-z0-9_]*\\}$", "").contains("*")) {
        throw new IllegalArgumentException("Expected an HTTP method and an exact MVC handler pattern");
      }
      if (rules.putIfAbsent(new Key(method, pattern), rule) != null) {
        throw new IllegalArgumentException("Duplicate product authorization route");
      }
      return this;
    }
  }

  static void checkText(String value) {
    if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 256) {
      throw new IllegalArgumentException("Invalid v1 text");
    }
  }
}
