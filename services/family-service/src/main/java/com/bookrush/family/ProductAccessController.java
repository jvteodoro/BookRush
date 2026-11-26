package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessRoutes.Action;
import com.bookrush.platform.security.ProductAccessRoutes.Resource;
import com.bookrush.platform.security.ProductAccessRoutes.Type;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProductAccessController {
  static final String PATH = "/api/internal/v1/product-access/decisions";
  private static final String CORRELATION = "X-Correlation-ID";
  private static final int MAX_BODY = 32 * 1024;
  private static final ObjectMapper PROBLEMS = new ObjectMapper();

  record Context(String clientId, String audience, List<String> scopes) {}
  record Request(TechnicalIdentity actor, Action action, Context authentication, Resource resource, String correlationId) {}
  record Parameters(ProductAccessEvaluator.Management management, UUID household, SupervisionStore.Scope scope) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Decision(boolean allowed, ProductPolicy.Reason reason, String correlationId, String policyVersion,
                  Map<String, String> factVersions, Instant validUntil) {}
  record Problem(int status, String code, String correlationId) {}

  private final ProductAccessWorkloads workloads;
  private final ProductAccessEvaluator evaluator;
  private final MeterRegistry metrics;
  private final String productIssuer;
  private final Clock clock;
  private final ObjectMapper requestJson;

  ProductAccessController(ProductAccessWorkloads workloads, ProductAccessEvaluator evaluator, MeterRegistry metrics,
      @Value("${bookrush.security.issuer}") String productIssuer, Clock clock, ObjectMapper json) {
    this.workloads = workloads;
    this.evaluator = evaluator;
    this.metrics = metrics;
    this.productIssuer = productIssuer;
    this.clock = clock;
    this.requestJson = json.copy().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
            DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
  }

  @PostMapping(value = "/api/internal/v1/product-access/decisions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  Object evaluate(@AuthenticationPrincipal Jwt token, HttpServletRequest http, HttpServletResponse response)
      throws IOException {
    String correlation = headerCorrelation(http);
    Request request;
    Parameters parameters;
    try {
      if (http.getContentType() == null || !MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(http.getContentType()))) {
        throw new IllegalArgumentException();
      }
      byte[] body = http.getInputStream().readNBytes(MAX_BODY + 1);
      if (body.length > MAX_BODY) throw new IllegalArgumentException();
      var tree = requestJson.readTree(body);
      if (tree != null && tree.path("correlationId").isTextual() && text(tree.path("correlationId").textValue())) {
        correlation = tree.path("correlationId").textValue();
      }
      var resource = tree.path("resource");
      if (resource.path("owner").isNull() || resource.path("id").isNull()) throw new IllegalArgumentException();
      request = requestJson.treeToValue(tree, Request.class);
      validate(request);
      validateJsonTypes(tree);
    } catch (IOException | RuntimeException invalid) {
      problem(response, 400, "INVALID_REQUEST", correlation);
      return null;
    }
    if (!workloads.permits(token.getClaimAsString("azp"), request.action(), request.resource().type())) {
      problem(response, 403, "CALLER_FORBIDDEN", correlation);
      return null;
    }

    try {
      parameters = parameters(request);
    } catch (IllegalArgumentException invalid) {
      problem(response, 400, "INVALID_REQUEST", correlation);
      return null;
    }

    long start = System.nanoTime();
    String result = "unavailable";
    try {
      var outcome = evaluator.evaluate(request.actor(), request.action(), request.resource(),
          parameters.management(), parameters.household(), parameters.scope());
      var now = clock.instant();
      var cutoff = now.plusSeconds(5);
      if (outcome.cutoff() != null && outcome.cutoff().isBefore(cutoff)) cutoff = outcome.cutoff();
      if (!now.isBefore(cutoff)) throw FamilyProblem.unavailable();
      boolean allowed = outcome.reason() == null;
      result = allowed ? "allowed" : "denied";
      response.setHeader("Cache-Control", "no-store");
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      return new Decision(allowed, outcome.reason(), correlation, "1", outcome.versions(), cutoff);
    } catch (FamilyProblem | DataAccessException | TransactionException unavailable) {
      problem(response, 503, "DECISION_UNAVAILABLE", correlation);
      return null;
    } finally {
      metrics.counter("bookrush.family.product.access.decisions", "outcome", result).increment();
      metrics.timer("bookrush.family.product.access.latency").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  private static void validateJsonTypes(JsonNode tree) {
    var context = tree.path("authentication");
    var resource = tree.path("resource");
    requireStrings(tree.path("actor"), "issuer", "subject");
    requireStrings(tree, "action", "correlationId");
    requireStrings(context, "clientId", "audience");
    requireStrings(resource, "type");
    if (resource.has("id")) requireStrings(resource, "id");
    if (resource.has("owner")) requireStrings(resource.path("owner"), "issuer", "subject");
    if (!context.path("scopes").isArray()) throw new IllegalArgumentException();
    for (var scope : context.path("scopes")) {
      if (!scope.isTextual()) throw new IllegalArgumentException();
    }
  }

  private static void requireStrings(JsonNode node, String... fields) {
    for (String field : fields) {
      if (!node.path(field).isTextual()) throw new IllegalArgumentException();
    }
  }

  private void validate(Request request) {
    if (request == null || request.actor() == null || request.action() == null
        || request.resource() == null || request.authentication() == null || !text(request.correlationId())) {
      throw new IllegalArgumentException();
    }
    var context = request.authentication();
    if (!text(context.clientId()) || !text(context.audience()) || context.scopes() == null
        || context.scopes().stream().anyMatch(scope -> !text(scope))
        || context.scopes().size() != new HashSet<>(context.scopes()).size()) {
      throw new IllegalArgumentException();
    }
    productIdentity(request.actor());
    if (request.resource().owner() != null) productIdentity(request.resource().owner());
  }

  private void productIdentity(TechnicalIdentity identity) {
    if (!productIssuer.equals(identity.issuer()) || !text(identity.issuer()) || !text(identity.subject())) {
      throw new IllegalArgumentException();
    }
  }

  private static Parameters parameters(Request request) {
    Resource resource = request.resource();
    return switch (request.action()) {
      case SESSION_VIEW -> forType(resource, Type.SESSION);
      case INVITE_SELF -> forType(resource, Type.INVITATION);
      case PUBLISH_READ, PUBLISH_WRITE, PUBLISH_WITHDRAW -> forType(resource, Type.SUBMISSION);
      case SUPERVISE_COMMENTS -> {
        forType(resource, Type.COMMENT);
        yield new Parameters(null, null, SupervisionStore.Scope.COMMENTS);
      }
      case SUPERVISE_HISTORY -> {
        forType(resource, Type.READING_HISTORY);
        var scope = switch (resource.id() == null ? "" : resource.id()) {
          case "books-accessed" -> SupervisionStore.Scope.BOOKS_ACCESSED;
          case "reading-activity" -> SupervisionStore.Scope.READING_ACTIVITY;
          default -> throw new IllegalArgumentException();
        };
        yield new Parameters(null, null, scope);
      }
      case FAMILY_MANAGE -> {
        forType(resource, Type.FAMILY);
        String[] parts = resource.id() == null ? new String[0] : resource.id().split(":", -1);
        if (parts.length == 0 || parts.length > 2) throw new IllegalArgumentException();
        var operation = ProductAccessEvaluator.Management.valueOf(parts[0]);
        if ((operation == ProductAccessEvaluator.Management.CREATE) != (parts.length == 1)) {
          throw new IllegalArgumentException();
        }
        UUID household = parts.length == 2 ? UUID.fromString(parts[1]) : null;
        if (household != null && !household.toString().equalsIgnoreCase(parts[1])) throw new IllegalArgumentException();
        yield new Parameters(operation, household, null);
      }
      case PRODUCT_USE -> {
        switch (resource.type()) {
          case CATALOG, BOOK, ASSET, FEED, PROFILE, READER_STATE, COMMENT, TELEMETRY -> { }
          default -> throw new IllegalArgumentException();
        }
        yield new Parameters(null, null, null);
      }
    };
  }

  private static Parameters forType(Resource resource, Type expected) {
    if (resource.type() != expected) throw new IllegalArgumentException();
    return new Parameters(null, null, null);
  }

  private static boolean text(String value) {
    return value != null && !value.isEmpty() && value.codePointCount(0, value.length()) <= 256;
  }

  static String headerCorrelation(HttpServletRequest request) {
    String correlation = request.getHeader(CORRELATION);
    return text(correlation) ? correlation : UUID.randomUUID().toString();
  }

  static void problem(HttpServletResponse response, int status, String code, String correlation) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("Cache-Control", "no-store");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    PROBLEMS.writeValue(response.getOutputStream(), new Problem(status, code, correlation));
  }
}
