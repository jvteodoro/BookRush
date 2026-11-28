package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessRoutes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static com.bookrush.family.SupervisionStore.*;

@RestController
class SupervisionController {
  record Request(@Valid @NotNull FamilyController.MembershipQuery minor,
                 @NotNull @Size(min = 1, max = 4) Set<@NotNull Scope> scopes) {}
  record Command(@Valid @NotNull FamilyController.MembershipQuery guardian,
                 @Valid @NotNull FamilyController.MembershipQuery minor,
                 @NotNull Action action, @NotNull @PositiveOrZero Long expectedVersion,
                 @Size(max = 4) Set<@NotNull Scope> scopes, Instant effectiveFrom,
                 Instant effectiveUntil, UUID verificationRef) {}
  record ScopeQuery(@Valid @NotNull FamilyController.MembershipQuery guardian,
                    @Valid @NotNull FamilyController.MembershipQuery minor, @NotNull Scope scope) {}

  private final SupervisionStore store;
  private final Environment environment;
  private final String issuer;
  private final ProductAccessWorkloads workloads;

  SupervisionController(SupervisionStore store, Environment environment, ProductAccessWorkloads workloads,
      @Value("${bookrush.security.issuer}") String issuer) {
    this.store = store;
    this.environment = environment;
    this.issuer = issuer;
    this.workloads = workloads;
  }

  @PostMapping("/api/v1/family/supervision")
  @ResponseStatus(HttpStatus.CREATED)
  Receipt request(@AuthenticationPrincipal Jwt token, @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody Request request) {
    return store.request(actor(token), identity(request.minor()), request.scopes(), key);
  }

  @PostMapping("/api/v1/family/{household}/members/{member}/supervision")
  @ResponseStatus(HttpStatus.CREATED)
  Receipt simulatedApproval(@AuthenticationPrincipal Jwt token, @PathVariable UUID household,
      @PathVariable UUID member, @RequestHeader("Idempotency-Key") UUID key) {
    if (!simulatedApprovalEnabled(environment)) {
      throw FamilyProblem.forbidden("SIMULATION_DISABLED");
    }
    return store.simulatedApproval(actor(token), household, member, key);
  }

  static boolean simulatedApprovalEnabled(Environment environment) {
    return environment.getProperty("family.supervision-simulated-approval", Boolean.class, false);
  }

  @GetMapping("/api/v1/family/supervision/me")
  List<View> own(@AuthenticationPrincipal Jwt token) {
    return store.own(actor(token));
  }

  @PostMapping("/api/internal/v1/family/supervision/admin/{id}")
  Receipt change(@AuthenticationPrincipal Jwt token, @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Command command) {
    if (!simulationEnabled(environment)) {
      throw FamilyProblem.forbidden("SIMULATION_DISABLED");
    }
    return store.change(actor(token), id, identity(command.guardian()), identity(command.minor()),
        new Change(command.action(), command.expectedVersion(), command.scopes() == null ? Set.of() : command.scopes(),
            command.effectiveFrom(), command.effectiveUntil(), command.verificationRef()), key);
  }

  @PostMapping("/api/internal/v1/family/supervision/evaluate")
  Facts scope(@Valid @RequestBody ScopeQuery query) {
    return store.scope(identity(query.guardian()), identity(query.minor()), query.scope());
  }

  record HistoryQuery(@Valid @NotNull FamilyController.MembershipQuery guardian,
                      @NotNull UUID memberId, @NotNull Scope scope) {}

  @PostMapping("/api/internal/v1/family/supervision/history-window")
  ReadingWindow historyWindow(@AuthenticationPrincipal Jwt token, @Valid @RequestBody HistoryQuery query) {
    var scope = query.scope();
    boolean comments = scope == Scope.COMMENTS;
    if (!comments && scope != Scope.BOOKS_ACCESSED && scope != Scope.READING_ACTIVITY) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }
    String client = token.getClaimAsString("azp");
    var action = comments ? ProductAccessRoutes.Action.SUPERVISE_COMMENTS : ProductAccessRoutes.Action.SUPERVISE_HISTORY;
    var type = comments ? ProductAccessRoutes.Type.COMMENT : ProductAccessRoutes.Type.READING_HISTORY;
    if (!(comments ? "bookrush-social-product-access-s2s" : "bookrush-reader-state-s2s").equals(client)
        || !workloads.permits(client, action, type)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    var window = store.readingWindow(identity(query.guardian()), query.memberId(), query.scope());
    if (window == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return window;
  }

  @PostMapping("/api/internal/v1/family/product-access/evaluate")
  Facts product(@Valid @RequestBody FamilyController.MembershipQuery target) {
    return store.product(identity(target));
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail unavailable() {
    return FamilyProblem.unavailable().getBody();
  }

  private TechnicalIdentity identity(FamilyController.MembershipQuery value) {
    if (!issuer.equals(value.issuer())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identity issuer is not the product issuer");
    }
    return new TechnicalIdentity(value.issuer(), value.subject());
  }

  static boolean simulationEnabled(Environment environment) {
    return environment.matchesProfiles("supervision-mock & !prod & !production")
        && Set.of("local", "development", "test")
            .contains(environment.getProperty("bookrush.observability.environment", "unknown"))
        && environment.getProperty("family.supervision-simulation", Boolean.class, false);
  }

  private static TechnicalIdentity actor(Jwt jwt) {
    return new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
  }
}
