package com.bookrush.family;

import com.bookrush.platform.core.AgePolicy;
import com.bookrush.platform.security.ProductAccessRoutes.Action;
import java.time.Instant;
import java.util.Map;

final class ProductPolicy {
  enum Reason {
    ACCOUNT_RESTRICTED, ACCOUNT_SUSPENDED, AGE_INELIGIBLE, ENTITLEMENT_REQUIRED,
    DELEGATION_REQUIRED, OWNERSHIP_REQUIRED, SUPERVISION_REQUIRED, TARGET_NOT_ELIGIBLE, SCOPE_REQUIRED
  }

  record Outcome(Reason reason, Instant cutoff, Map<String, String> versions) {
    static Outcome allow(Instant cutoff, Map<String, String> versions) {
      return new Outcome(null, cutoff, Map.copyOf(versions));
    }

    static Outcome deny(Reason reason, Map<String, String> versions) {
      return new Outcome(reason, null, Map.copyOf(versions));
    }
  }

  static Reason eligibility(Action action, KeycloakAgeFacts.Account account, boolean verifiedRequired) {
    if (!account.enabled()) return Reason.ACCOUNT_SUSPENDED;
    return switch (action) {
      case SESSION_VIEW, INVITE_SELF, SUPERVISE_HISTORY, SUPERVISE_COMMENTS -> null;
      case PRODUCT_USE -> account.age().meetsEvidenceRequirement(verifiedRequired) ? null : Reason.ACCOUNT_RESTRICTED;
      case PUBLISH_READ, PUBLISH_WRITE, PUBLISH_WITHDRAW, FAMILY_MANAGE ->
          account.age().state() == AgePolicy.State.ADULT && account.age().meetsEvidenceRequirement(verifiedRequired)
              ? null : Reason.AGE_INELIGIBLE;
    };
  }

  static Outcome entitlement(SubscriptionEntitlements.Evaluation entitlement, Map<String, String> versions) {
    return entitlement.enabled() ? Outcome.allow(entitlement.effectiveUntil(), versions)
        : Outcome.deny(Reason.ENTITLEMENT_REQUIRED, versions);
  }

  static Outcome supervised(SupervisionStore.Facts facts, Map<String, String> versions) {
    if (facts.grantVersion() != null) versions.put("supervision", facts.grantVersion().toString());
    return facts.allowed() ? Outcome.allow(facts.validUntil(), versions)
        : Outcome.deny(Reason.valueOf(facts.reason()), versions);
  }

  private ProductPolicy() {}
}
