package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Conditional(MockBillingEnabled.class)
class MockBillingController {
  record Simulation(@Valid @NotNull SubscriptionController.Identity owner,
                    @NotNull MockBillingProvider.Scenario scenario) {}
  record Receipt(boolean simulation, boolean realPayment, UUID externalEventId, String state, UUID billingEventId) {}

  private final MockBillingProvider provider;
  private final BillingInbox inbox;
  private final String productIssuer;
  private final MeterRegistry metrics;

  MockBillingController(MockBillingProvider provider, BillingInbox inbox, MeterRegistry metrics,
      @Value("${bookrush.security.issuer}") String productIssuer) {
    this.provider = provider;
    this.inbox = inbox;
    this.metrics = metrics;
    this.productIssuer = productIssuer;
  }

  @PostMapping("/api/v1/subscription/checkout")
  ResponseEntity<Receipt> checkout(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") UUID requestId) {
    var owner = identity(jwt);
    return deliver(provider.checkout(owner, requestId), owner);
  }

  @PostMapping("/api/internal/v1/subscription/billing/mock/simulate")
  ResponseEntity<Receipt> simulate(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") UUID requestId,
      @Valid @RequestBody Simulation simulation) {
    if (!productIssuer.equals(simulation.owner().issuer())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "owner issuer is not the product issuer");
    }
    if (simulation.scenario() == MockBillingProvider.Scenario.APPROVE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "use the authenticated checkout for approval");
    }
    var owner = new TechnicalIdentity(simulation.owner().issuer(), simulation.owner().subject());
    return deliver(provider.simulate(owner, requestId, simulation.scenario()), identity(jwt));
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail unavailable() {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Mock billing state unavailable");
    problem.setProperty("code", "DECISION_UNAVAILABLE");
    return problem;
  }

  private ResponseEntity<Receipt> deliver(BillingProvider.Delivery delivery, TechnicalIdentity agent) {
    if (delivery.event() == null) {
      metrics.counter("bookrush.subscription.mock.receipts", "result", "DECLINED").increment();
      return ResponseEntity.ok(new Receipt(true, false, delivery.externalEventId(), "DECLINED", null));
    }
    var result = inbox.submit(delivery.event(), agent);
    metrics.counter("bookrush.subscription.mock.receipts", "result", result.state().name()).increment();
    var status = switch (result.state()) {
      case APPLIED -> HttpStatus.OK;
      case PENDING -> HttpStatus.ACCEPTED;
      case STALE -> HttpStatus.CONFLICT;
    };
    return ResponseEntity.status(status)
        .body(new Receipt(true, false, delivery.externalEventId(), result.state().name(), result.eventId()));
  }

  private static TechnicalIdentity identity(Jwt jwt) {
    return new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
  }
}
