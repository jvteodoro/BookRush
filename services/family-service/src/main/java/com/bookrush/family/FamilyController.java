package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@RestController
class FamilyController {
  record InvitationRequest(@NotNull UUID recipientCode) {}

  private final FamilyStore store;

  FamilyController(FamilyStore store) {
    this.store = store;
  }

  @PostMapping("/api/v1/family")
  @ResponseStatus(HttpStatus.CREATED)
  FamilyStore.Household create(@AuthenticationPrincipal Jwt token) {
    return store.create(identity(token));
  }

  @GetMapping("/api/v1/family/me")
  FamilyStore.HouseholdView mine(@AuthenticationPrincipal Jwt token) {
    return store.mine(identity(token));
  }

  @PostMapping("/api/v1/family/invite-address")
  FamilyStore.Address address(@AuthenticationPrincipal Jwt token) {
    return store.address(identity(token));
  }

  @PostMapping("/api/v1/family/{id}/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  FamilyStore.Invitation invite(@AuthenticationPrincipal Jwt token, @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") UUID requestId, @Valid @RequestBody InvitationRequest request) {
    return store.invite(identity(token), id, requestId, request.recipientCode());
  }

  @GetMapping("/api/v1/family/{id}/invitations")
  List<FamilyStore.Invitation> sent(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
    return store.sent(identity(token), id);
  }

  @GetMapping("/api/v1/family/invitations/me")
  List<FamilyStore.Invitation> received(@AuthenticationPrincipal Jwt token) {
    return store.received(identity(token));
  }

  @DeleteMapping("/api/v1/family/{id}/invitations/{invitationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(@AuthenticationPrincipal Jwt token, @PathVariable UUID id, @PathVariable UUID invitationId) {
    store.cancel(identity(token), id, invitationId);
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail unavailable() {
    return FamilyProblem.unavailable().getBody();
  }

  private static TechnicalIdentity identity(Jwt token) {
    return new TechnicalIdentity(token.getIssuer().toString(), token.getSubject());
  }
}
