package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static com.bookrush.subscription.CommercialPolicy.*;

@RestController
class SubscriptionController {
  record Identity(@NotBlank @Size(max = 512) String issuer, @NotBlank @Size(max = 255) String subject) {}
  record EvaluationRequest(@Valid @NotNull Identity owner, @NotNull Entitlement entitlement) {}
  record Evaluation(boolean enabled, Instant effectiveUntil, long version) {}
  record Rights(boolean publishBooks, boolean premiumFamily, int additionalSeats, Instant effectiveUntil, long version) {}
  record TransitionRequest(@Valid @NotNull Identity owner, @NotNull UUID eventId, @Positive long sourceVersion,
                           @NotNull Operation operation, Instant startsAt, Instant endsAt) {}
  record Receipt(long version) {}
  record BillingDelivery(
      @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,63}") String billingSource,
      @NotBlank @Size(max = 160) @Pattern(regexp = "[A-Za-z0-9_.:-]+") String externalEventId,
      @Valid @NotNull Identity owner, @Positive long sourceVersion,
      @NotNull Operation operation, Instant startsAt, Instant endsAt) {}

  private final SubscriptionStore store;
  private final BillingInbox billing;
  private final String productIssuer;
  private final MeterRegistry metrics;

  SubscriptionController(SubscriptionStore store, BillingInbox billing, @Value("${bookrush.security.issuer}") String productIssuer,
                         MeterRegistry metrics) {
    this.store = store;
    this.billing = billing;
    this.productIssuer = productIssuer;
    this.metrics = metrics;
  }

  @GetMapping("/api/v1/subscription/me")
  SubscriptionStore.Snapshot me(@AuthenticationPrincipal Jwt token) {
    return store.snapshot(identity(token));
  }

  @PostMapping("/api/v1/subscription/cancel")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Receipt cancel(@AuthenticationPrincipal Jwt token) {
    var owner = identity(token);
    var snapshot = store.snapshot(owner);
    if (snapshot.state() != State.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "no active subscription to cancel");
    }
    long version = store.apply(owner, owner, new SubscriptionStore.BillingEvent(UUID.randomUUID(),
        snapshot.version() + 1, Operation.CANCEL_AT_END, null, null));
    return new Receipt(version);
  }

  @GetMapping("/api/v1/subscription/me/entitlements")
  Rights entitlements(@AuthenticationPrincipal Jwt token) {
    var snapshot = me(token);
    return new Rights(snapshot.publishBooks(), snapshot.premiumFamily(), snapshot.additionalSeats(),
        snapshot.effectiveUntil(), snapshot.version());
  }

  @PostMapping("/api/internal/v1/subscription/entitlements/evaluate")
  Evaluation evaluate(@Valid @RequestBody EvaluationRequest request) {
    var snapshot = store.snapshot(owner(request.owner()));
    boolean enabled = switch (request.entitlement()) {
      case PUBLISH_BOOKS -> snapshot.publishBooks();
      case PREMIUM_FAMILY -> snapshot.premiumFamily();
    };
    metrics.counter("bookrush.subscription.evaluations", "entitlement", request.entitlement().name(),
        "enabled", Boolean.toString(enabled)).increment();
    return new Evaluation(enabled, enabled ? snapshot.effectiveUntil() : null, snapshot.version());
  }

  @PostMapping("/api/internal/v1/subscription/transitions")
  Receipt transition(@Valid @RequestBody TransitionRequest request, @AuthenticationPrincipal Jwt token) {
    return new Receipt(store.apply(owner(request.owner()), identity(token),
        new SubscriptionStore.BillingEvent(request.eventId(), request.sourceVersion(), request.operation(),
            request.startsAt(), request.endsAt())));
  }

  @PostMapping("/api/internal/v1/subscription/billing/events")
  ResponseEntity<BillingInbox.Receipt> billingEvent(@Valid @RequestBody BillingDelivery request,
      @AuthenticationPrincipal Jwt token) {
    if ("mock".equals(request.billingSource())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reserved billing source");
    }
    var event = new BillingInbox.Event(request.billingSource(), request.externalEventId(), owner(request.owner()),
        request.sourceVersion(), request.operation(), request.startsAt(), request.endsAt());
    var receipt = billing.submit(event, identity(token));
    return ResponseEntity.status(receipt.state() == BillingInbox.Status.PENDING
        ? HttpStatus.ACCEPTED : HttpStatus.OK).body(receipt);
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail unavailable(RuntimeException failure) {
    metrics.counter("bookrush.subscription.storage.failures").increment();
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Commercial state unavailable");
    problem.setProperty("code", "DECISION_UNAVAILABLE");
    return problem;
  }

  private TechnicalIdentity owner(Identity owner) {
    if (!productIssuer.equals(owner.issuer())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "owner issuer is not the product issuer");
    }
    return new TechnicalIdentity(owner.issuer(), owner.subject());
  }

  private static TechnicalIdentity identity(Jwt token) {
    return new TechnicalIdentity(token.getIssuer().toString(), token.getSubject());
  }
}
