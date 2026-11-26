package com.bookrush.family;

import com.bookrush.platform.core.AgePolicy;
import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessRoutes.Action;
import com.bookrush.platform.security.ProductAccessRoutes.Resource;
import com.bookrush.platform.security.ProductAccessRoutes.Type;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.bookrush.family.ProductPolicy.Reason.*;

@Service
class ProductAccessEvaluator {
  enum Management {
    CREATE(true), READ(false), INVITE(true), CANCEL(false), REMOVE(false), SUSPEND(false),
    REACTIVATE(true), GRANT_PUBLICATION(true), REVOKE_PUBLICATION(false);

    final boolean premium;
    Management(boolean premium) { this.premium = premium; }
  }

  private final KeycloakAgeFacts identity;
  private final FamilyStore families;
  private final SubscriptionEntitlements subscription;
  private final SupervisionStore supervision;
  private final boolean verificationRequired;

  ProductAccessEvaluator(KeycloakAgeFacts identity, FamilyStore families, SubscriptionEntitlements subscription,
      SupervisionStore supervision,
      @Value("${family.product-access-age-verification-required}") boolean verificationRequired) {
    this.identity = identity;
    this.families = families;
    this.subscription = subscription;
    this.supervision = supervision;
    this.verificationRequired = verificationRequired;
  }

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public ProductPolicy.Outcome evaluate(TechnicalIdentity actor, Action action, Resource resource, Management management,
      UUID household, SupervisionStore.Scope scope) {
    var versions = new LinkedHashMap<String, String>();
    boolean supervised = action == Action.SUPERVISE_HISTORY || action == Action.SUPERVISE_COMMENTS;
    if (supervised) {
      if (resource.owner() == null || actor.equals(resource.owner())) {
        return ProductPolicy.Outcome.deny(OWNERSHIP_REQUIRED, versions);
      }
      return ProductPolicy.supervised(supervision.scope(actor, resource.owner(), scope), versions);
    }

    var account = identity.read(actor);
    return evaluateCurrent(actor, action, resource, management, household, versions, account);
  }

  private ProductPolicy.Outcome evaluateCurrent(TechnicalIdentity actor, Action action, Resource resource,
      Management management, UUID household, Map<String, String> versions, KeycloakAgeFacts.Account account) {
    var reason = ProductPolicy.eligibility(action, account, verificationRequired);
    if (reason != null) return ProductPolicy.Outcome.deny(reason, versions);
    if ((action != Action.PRODUCT_USE || resource.owner() != null) && !actor.equals(resource.owner())) {
      return ProductPolicy.Outcome.deny(OWNERSHIP_REQUIRED, versions);
    }
    return switch (action) {
      case SESSION_VIEW, INVITE_SELF -> ProductPolicy.Outcome.allow(null, versions);
      case PRODUCT_USE -> product(actor, account, versions);
      case FAMILY_MANAGE -> manage(actor, management, household, versions);
      case PUBLISH_READ, PUBLISH_WRITE, PUBLISH_WITHDRAW -> publish(actor, versions);
      default -> throw new IllegalStateException("Unexpected product action");
    };
  }

  enum AccessState {
    ADULT_ELIGIBLE, MINOR_AUTHORIZED, MINOR_AWAITING_AUTHORIZATION, MINOR_FAMILY_INACTIVE,
    AGE_PROFILE_INCOMPLETE, SUSPENDED
  }

  record Session(AccessState accessState, ProductPolicy.Reason reasonCode, List<Action> capabilities,
                 Instant validUntil, String correlationId) {}

  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Session session(TechnicalIdentity actor, boolean invitationScope, String correlationId, Clock clock) {
    var account = identity.read(actor);
    var own = new Resource(Type.SESSION, null, actor);
    var canView = evaluateCurrent(actor, Action.SESSION_VIEW, own, null, null, Map.of(), account);
    if (canView.reason() != null) {
      // A denied SESSION_VIEW may disclose only the suspension status to its authenticated owner.
      return new Session(AccessState.SUSPENDED, canView.reason(), List.of(), clock.instant().plusSeconds(60),
          correlationId);
    }
    var assessment = new LinkedHashMap<String, String>();
    var canUse = evaluateCurrent(actor, Action.PRODUCT_USE, new Resource(Type.READER_STATE, null, actor),
        null, null, assessment, account);
    var capabilities = new java.util.ArrayList<Action>();
    capabilities.add(Action.SESSION_VIEW);
    if (canUse.reason() == null) capabilities.add(Action.PRODUCT_USE);
    if (invitationScope && evaluateCurrent(actor, Action.INVITE_SELF, new Resource(Type.INVITATION, null, actor),
        null, null, Map.of(), account).reason() == null) {
      capabilities.add(Action.INVITE_SELF);
    }
    AccessState state;
    if (!account.age().meetsEvidenceRequirement(verificationRequired)) {
      state = AccessState.AGE_PROFILE_INCOMPLETE;
    } else if (canUse.reason() == null) {
      state = account.age().state() == AgePolicy.State.ADULT ? AccessState.ADULT_ELIGIBLE : AccessState.MINOR_AUTHORIZED;
    } else {
      state = canUse.reason() == SUPERVISION_REQUIRED
          ? AccessState.MINOR_AWAITING_AUTHORIZATION : AccessState.MINOR_FAMILY_INACTIVE;
    }
    Instant now = clock.instant();
    Instant validity = now.plusSeconds(60);
    if (canUse.cutoff() != null && canUse.cutoff().isBefore(validity)) validity = canUse.cutoff();
    var adultFrom = account.age().adultFrom();
    if (account.age().state() == AgePolicy.State.MINOR && adultFrom.isBefore(validity)) validity = adultFrom;
    if (!now.isBefore(validity)) throw FamilyProblem.unavailable();
    return new Session(state, canUse.reason(), List.copyOf(capabilities), validity, correlationId);
  }

  private ProductPolicy.Outcome manage(TechnicalIdentity actor, Management operation, UUID household,
      Map<String, String> versions) {
    if (operation != Management.CREATE && !families.ownsHousehold(actor, household)) {
      return ProductPolicy.Outcome.deny(OWNERSHIP_REQUIRED, versions);
    }
    if (!operation.premium) return ProductPolicy.Outcome.allow(null, versions);
    return ProductPolicy.entitlement(entitlement(actor, "PREMIUM_FAMILY", "subscription", versions), versions);
  }

  private ProductPolicy.Outcome product(TechnicalIdentity actor, KeycloakAgeFacts.Account account,
      Map<String, String> versions) {
    if (account.age().state() == AgePolicy.State.ADULT) return ProductPolicy.Outcome.allow(null, versions);
    var member = member(actor, versions);
    if (!member.facts().state().equals("ACTIVE")) return ProductPolicy.Outcome.deny(ACCOUNT_RESTRICTED, versions);
    var payment = entitlement(member.holder(), "PREMIUM_FAMILY", "holderSubscription", versions);
    return ProductPolicy.supervised(supervision.minorProduct(actor, account, payment.effectiveUntil()), versions);
  }

  private ProductPolicy.Outcome publish(TechnicalIdentity actor, Map<String, String> versions) {
    var own = entitlement(actor, "PUBLISH_BOOKS", "subscription", versions);
    if (own.enabled()) return ProductPolicy.entitlement(own, versions);
    var member = member(actor, versions);
    if (!member.facts().state().equals("ACTIVE")) return ProductPolicy.Outcome.deny(ENTITLEMENT_REQUIRED, versions);
    versions.put("delegation", Long.toString(member.facts().permissionVersion()));
    if (!member.facts().delegationEnabled()) return ProductPolicy.Outcome.deny(DELEGATION_REQUIRED, versions);
    return ProductPolicy.entitlement(entitlement(member.holder(), "PREMIUM_FAMILY", "holderSubscription", versions),
        versions);
  }

  private FamilyStore.LocalMembership member(TechnicalIdentity actor, Map<String, String> versions) {
    var member = families.localMembership(actor);
    if (member.facts().membershipId() != null) {
      versions.put("membership", Long.toString(member.facts().membershipVersion()));
    }
    return member;
  }

  private SubscriptionEntitlements.Evaluation entitlement(TechnicalIdentity owner, String entitlement, String key,
      Map<String, String> versions) {
    var result = subscription.evaluate(owner, entitlement);
    versions.put(key, result.version().toString());
    return result;
  }
}
