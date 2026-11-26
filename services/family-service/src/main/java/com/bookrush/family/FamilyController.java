package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
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

  enum TargetState { ACTIVE, SUSPENDED }
  record StateChange(@NotNull TargetState state, @NotNull @PositiveOrZero Long expectedVersion) {}
  record DelegationChange(@NotNull Boolean canPublish, @NotNull @PositiveOrZero Long expectedVersion) {}
  record MembershipQuery(@NotBlank @Size(max = 512) String issuer, @NotBlank @Size(max = 255) String subject) {}

  private final FamilyStore store;
  private final String productIssuer;

  FamilyController(FamilyStore store, @Value("${bookrush.security.issuer}") String issuer) {
    this.store = store;
    this.productIssuer = issuer;
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

  @PostMapping("/api/v1/family/invitations/{id}/accept")
  FamilyStore.Accepted accept(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
    return store.accept(identity(token), id);
  }

  @PostMapping("/api/v1/family/invitations/{id}/decline")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void decline(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
    store.decline(identity(token), id);
  }

  @GetMapping("/api/v1/family/{id}/members")
  List<FamilyStore.Member> members(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
    return store.members(identity(token), id);
  }

  @DeleteMapping("/api/v1/family/{id}/members/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@AuthenticationPrincipal Jwt token, @PathVariable UUID id, @PathVariable UUID memberId,
      @RequestParam long expectedVersion) {
    if (expectedVersion < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "negative membership version");
    }
    store.changeState(identity(token), id, memberId, FamilyStore.MemberState.REMOVED, expectedVersion);
  }

  @PutMapping("/api/v1/family/{id}/members/{memberId}/state")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changeState(@AuthenticationPrincipal Jwt token, @PathVariable UUID id, @PathVariable UUID memberId,
      @Valid @RequestBody StateChange request) {
    store.changeState(identity(token), id, memberId, FamilyStore.MemberState.valueOf(request.state().name()),
        request.expectedVersion());
  }

  @GetMapping("/api/v1/family/{id}/members/{memberId}/publication")
  FamilyStore.Member permission(@AuthenticationPrincipal Jwt token, @PathVariable UUID id, @PathVariable UUID memberId) {
    return store.permission(identity(token), id, memberId);
  }

  @PutMapping("/api/v1/family/{id}/members/{memberId}/publication")
  FamilyStore.Member changePermission(@AuthenticationPrincipal Jwt token, @PathVariable UUID id,
      @PathVariable UUID memberId, @Valid @RequestBody DelegationChange request) {
    return store.changePermission(identity(token), id, memberId, request.canPublish(), request.expectedVersion());
  }

  @PostMapping("/api/internal/v1/family/membership/evaluate")
  FamilyStore.MembershipFacts membership(@Valid @RequestBody MembershipQuery request) {
    if (!productIssuer.equals(request.issuer())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identity issuer is not the product issuer");
    }
    return store.membershipFacts(new TechnicalIdentity(request.issuer(), request.subject()));
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ProblemDetail unavailable() {
    return FamilyProblem.unavailable().getBody();
  }

  private static TechnicalIdentity identity(Jwt token) {
    return new TechnicalIdentity(token.getIssuer().toString(), token.getSubject());
  }
}
