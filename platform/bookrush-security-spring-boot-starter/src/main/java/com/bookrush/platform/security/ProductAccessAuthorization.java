package com.bookrush.platform.security;

import com.bookrush.platform.core.CorrelationId;
import com.bookrush.platform.core.TechnicalIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Product gate for MVC handlers, independent of which bearer filter chain authenticated the request. */
public final class ProductAccessAuthorization implements HandlerInterceptor,
    AuthorizationManager<ProductAccessAuthorization.Operation> {
  private static final String HEADER = "X-Correlation-ID";
  private static final String CORRELATION = ProductAccessAuthorization.class.getName() + ".correlation";
  private final ProductAccessRoutes routes;
  private final ProductDecisionClient client;
  private final Clock clock;
  private final ObjectMapper json = new ObjectMapper();

  record Operation(HttpServletRequest request, ProductAccessRoutes.Policy policy, String correlation) {}

  ProductAccessAuthorization(ProductAccessRoutes routes, ProductDecisionClient client, Clock clock) {
    this.routes = routes;
    this.client = client;
    this.clock = clock;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
    // Spring MVC's preflight handler enforces the configured CORS rules without invoking the operation.
    if (!(handler instanceof HandlerMethod)) return true;
    String correlation = correlation(request, response.getHeader(HEADER));
    response.setHeader(HEADER, correlation);
    try {
      enforce(request, false);
      return true;
    } catch (ResponseStatusException denied) {
      if (!(denied instanceof Failure) && denied.getStatusCode().value() != 404) throw denied;
      writeProblem(request, response, denied);
      return false;
    }
  }

  void writeProblem(HttpServletRequest request, HttpServletResponse response, ResponseStatusException denied)
      throws IOException {
    String correlation = correlation(request, response.getHeader(HEADER));
    int status = denied.getStatusCode().value();
    response.setHeader(HEADER, correlation);
    response.setStatus(status);
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    response.setHeader("Cache-Control", "no-store");
    if (!"HEAD".equals(request.getMethod())) {
      response.setContentType("application/problem+json");
      byte[] body = json.writeValueAsBytes(Map.of("status", status, "code", code(status),
          "correlationId", correlation));
      response.getOutputStream().write(body);
    }
  }

  /**
   * Re-resolve ownership and obtain a fresh decision at the final operation boundary.
   * Use on the original MVC request, with the request's authenticated SecurityContext.
   */
  public void recheck(HttpServletRequest request) {
    enforce(request, true);
  }

  /** Returns only the requested subjects currently eligible for third-party profile discovery. */
  public Set<String> discoverableProfiles(HttpServletRequest request, Set<String> subjects) {
    if (subjects.isEmpty()) return Set.of();
    if (subjects.size() > 500) throw new IllegalArgumentException("Too many profile candidates");
    subjects.forEach(ProductAccessRoutes::checkText);
    try {
      return client.discoverableProfiles(Set.copyOf(subjects), correlation(request, null));
    } catch (ProductDecisionClient.Unavailable unavailable) {
      throw unavailable();
    }
  }

  public record HistoryWindow(TechnicalIdentity minor, Long version, java.time.Instant from,
                              java.time.Instant until, java.time.Instant validUntil) {}

  public enum SupervisionScope {
    BOOKS_ACCESSED(ProductAccessRoutes.Action.SUPERVISE_HISTORY, ProductAccessRoutes.Type.READING_HISTORY, "books-accessed"),
    READING_ACTIVITY(ProductAccessRoutes.Action.SUPERVISE_HISTORY, ProductAccessRoutes.Type.READING_HISTORY, "reading-activity"),
    COMMENTS(ProductAccessRoutes.Action.SUPERVISE_COMMENTS, ProductAccessRoutes.Type.COMMENT, null);

    final ProductAccessRoutes.Action action;
    final ProductAccessRoutes.Type type;
    final String selector;
    SupervisionScope(ProductAccessRoutes.Action action, ProductAccessRoutes.Type type, String selector) {
      this.action = action;
      this.type = type;
      this.selector = selector;
    }
  }

  /** Resolves only a grant owned by the authenticated guardian, then checks the canonical action. */
  public HistoryWindow supervisedWindow(HttpServletRequest request, JwtAuthenticationToken guardian,
      java.util.UUID memberId, SupervisionScope scope) {
    String correlation = supervisionCorrelation(request);
    var jwt = guardian.getToken();
    var actor = new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
    try {
      var window = client.historyWindow(actor, memberId, scope.name(), correlation);
      if (window == null) throw failure(HttpStatus.NOT_FOUND);
      request.setAttribute(HistoryWindow.class.getName(), window.minor());
      if (window.version() == null) throw failure(HttpStatus.FORBIDDEN);
      var resource = new ProductAccessRoutes.Resource(scope.type, scope.selector, window.minor());
      if (!client.allowed(new ProductDecisionClient.Request(actor, scope.action,
          new ProductDecisionClient.Authentication(jwt.getClaimAsString("azp"), "bookrush-reader-bff",
              scopes(jwt.getClaims().get("scope"))), resource, correlation))) {
        throw failure(HttpStatus.FORBIDDEN);
      }
      return window;
    } catch (ProductDecisionClient.Unavailable e) {
      throw unavailable();
    }
  }

  public static String supervisionCorrelation(HttpServletRequest request) {
    return correlation(request, null);
  }

  static TechnicalIdentity supervisedTarget(HttpServletRequest request) {
    return (TechnicalIdentity) request.getAttribute(HistoryWindow.class.getName());
  }

  private void enforce(HttpServletRequest request, boolean requireProtected) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    var rule = pattern == null ? null : routes.rule(request.getMethod(), pattern.toString());
    if (rule == null || (requireProtected && rule.policy() == null)) throw unavailable();
    if (rule.policy() == null) return;
    var operation = new Operation(request, rule.policy(), correlation(request, null));
    var decision = check(SecurityContextHolder.getContext()::getAuthentication, operation);
    if (!decision.isGranted()) throw failure(HttpStatus.FORBIDDEN);
  }

  @Override
  public AuthorizationDecision check(Supplier<Authentication> authentication, Operation operation) {
    var policy = operation.policy();
    if (!(authentication.get() instanceof JwtAuthenticationToken principal) || !principal.isAuthenticated()) {
      throw failure(HttpStatus.UNAUTHORIZED);
    }
    var jwt = principal.getToken();
    var claims = jwt.getClaims();
    if (!policy.issuer().equals(jwt.getClaimAsString("iss"))) throw failure(HttpStatus.UNAUTHORIZED);
    if (jwt.getAudience() == null || !jwt.getAudience().contains(policy.audience()) || jwt.getExpiresAt() == null
        || !clock.instant().isBefore(jwt.getExpiresAt())) throw failure(HttpStatus.UNAUTHORIZED);
    Object azp = claims.get("azp");
    Set<String> scopes = scopes(claims.get("scope"));
    if (!(azp instanceof String caller) || !policy.humanClients().contains(caller)
        || !scopes.containsAll(policy.requiredScopes())) return new AuthorizationDecision(false);
    TechnicalIdentity identity;
    try {
      ProductAccessRoutes.checkText(jwt.getSubject());
      scopes.forEach(ProductAccessRoutes::checkText);
      identity = new TechnicalIdentity(policy.issuer(), jwt.getSubject());
    } catch (IllegalArgumentException malformed) {
      throw failure(HttpStatus.UNAUTHORIZED);
    }
    var resource = policy.resolver().resolve(operation.request(), identity);
    if (resource == null || resource.type() != policy.type() || (policy.ownerRequired() && resource.owner() == null)) {
      throw unavailable();
    }
    if ((policy.action() == ProductAccessRoutes.Action.SESSION_VIEW
        || policy.action() == ProductAccessRoutes.Action.INVITE_SELF) && !identity.equals(resource.owner())) {
      throw failure(HttpStatus.NOT_FOUND);
    }
    try {
      return new AuthorizationDecision(client.allowed(new ProductDecisionClient.Request(identity, policy.action(),
          new ProductDecisionClient.Authentication(caller, policy.audience(), scopes), resource,
          operation.correlation())));
    } catch (ProductDecisionClient.Unavailable e) {
      throw unavailable();
    }
  }

  static Set<String> scopes(Object claim) {
    if (!(claim instanceof String value)) return Set.of();
    return Arrays.stream(value.split(" ")).filter(scope -> !scope.isBlank()).collect(Collectors.toUnmodifiableSet());
  }

  private static String correlation(HttpServletRequest request, String existingHeader) {
    if (request.getAttribute(CORRELATION) instanceof String existing) return existing;
    String candidate = existingHeader;
    if (candidate == null) candidate = request.getHeader(HEADER);
    String value;
    try {
      value = candidate == null ? UUID.randomUUID().toString() : new CorrelationId(candidate).value();
    } catch (IllegalArgumentException invalid) {
      value = UUID.randomUUID().toString();
    }
    request.setAttribute(CORRELATION, value);
    return value;
  }

  private static ResponseStatusException unavailable() {
    return failure(HttpStatus.SERVICE_UNAVAILABLE);
  }

  private static ResponseStatusException failure(HttpStatus status) {
    return new Failure(status);
  }

  static final class Failure extends ResponseStatusException {
    Failure(HttpStatus status) {
      super(status, code(status.value()));
    }
  }

  private static String code(int status) {
    return switch (status) {
      case 401 -> "INVALID_AUTHENTICATION";
      case 403 -> "CALLER_FORBIDDEN";
      case 404 -> "NOT_FOUND";
      default -> "DECISION_UNAVAILABLE";
    };
  }
}
