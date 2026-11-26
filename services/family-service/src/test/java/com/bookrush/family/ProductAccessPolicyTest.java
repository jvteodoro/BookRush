package com.bookrush.family;

import com.bookrush.platform.core.AgePolicy;
import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessRoutes.Action;
import com.bookrush.platform.security.ProductAccessRoutes.Resource;
import com.bookrush.platform.security.ProductAccessRoutes.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductAccessPolicyTest {
  private static final TechnicalIdentity ACTOR = new TechnicalIdentity("product", "actor");
  private static final TechnicalIdentity CHILD = new TechnicalIdentity("product", "child");
  private static final TechnicalIdentity HOLDER = new TechnicalIdentity("product", "holder");

  static Stream<String> vectors() throws Exception {
    var fixture = Path.of("src/test/resources/age-supervision-fixtures.tsv");
    if (!Files.exists(fixture)) fixture = Path.of("../../docs/productization/age-supervision-v1/fixtures.tsv");
    return Files.readAllLines(fixture).stream().skip(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("vectors")
  void replaysAgeAndSupervisionVectors(String row) {
    var v = row.split("\t");
    var now = Instant.parse(v[2]);
    var clock = Clock.fixed(now, ZoneOffset.UTC);
    var age = AgePolicy.classify(v[1].equals("—") ? List.of() : List.of(v[1]), AgePolicy.Provenance.DECLARED, clock);
    var identity = mock(KeycloakAgeFacts.class);
    var families = mock(FamilyStore.class);
    var billing = mock(SubscriptionEntitlements.class);
    var supervision = mock(SupervisionStore.class);
    boolean requireVerification = v[3].contains("verification required");
    var eval = new ProductAccessEvaluator(identity, families, billing, supervision, requireVerification);
    var action = Action.valueOf(v[4].split(":")[0]);
    Type type;
    TechnicalIdentity owner = ACTOR;
    SupervisionStore.Scope scope = null;
    when(identity.read(ACTOR)).thenReturn(new KeycloakAgeFacts.Account(true, age));

    switch (action) {
      case PRODUCT_USE -> type = Type.BOOK;
      case PUBLISH_WRITE -> type = Type.SUBMISSION;
      case SESSION_VIEW -> type = Type.SESSION;
      case INVITE_SELF -> type = Type.INVITATION;
      case SUPERVISE_HISTORY, SUPERVISE_COMMENTS -> {
        owner = CHILD;
        type = action == Action.SUPERVISE_HISTORY ? Type.READING_HISTORY : Type.COMMENT;
        scope = action == Action.SUPERVISE_COMMENTS ? SupervisionStore.Scope.COMMENTS
            : v[4].endsWith("reading-activity") ? SupervisionStore.Scope.READING_ACTIVITY : SupervisionStore.Scope.BOOKS_ACCESSED;
        boolean scoped = switch (v[0]) {
          case "partial_books", "expired_guardian", "scope_window_90_days", "scope_grant_window", "old_jwt_majority" -> true;
          default -> false;
        };
        String reason = age.state() == AgePolicy.State.ADULT ? "TARGET_NOT_ELIGIBLE" : "SUPERVISION_REQUIRED";
        var result = scoped && age.state() == AgePolicy.State.MINOR
            ? new SupervisionStore.Facts(true, null, now.plusSeconds(60), UUID.randomUUID(), 7L, now.minusSeconds(60), now)
            : SupervisionStore.Facts.denied(reason, now);
        when(supervision.scope(ACTOR, CHILD, scope)).thenReturn(result);
      }
      default -> throw new AssertionError("Unclassified vector action " + action);
    }
    boolean member = v[3].contains("active member") || v[0].equals("expired_minor")
        || v[0].equals("adult_no_automatic_delegation");
    boolean delegated = v[3].contains("delegated");
    var view = new FamilyStore.MembershipFacts(member ? "ACTIVE" : "ABSENT", null,
        member ? UUID.randomUUID() : null, delegated, 3, member ? 2 : 0, null);
    when(families.localMembership(ACTOR)).thenReturn(new FamilyStore.LocalMembership(view, member ? HOLDER : null));
    var own = new SubscriptionEntitlements.Evaluation(false, null, 0L);
    var premium = new SubscriptionEntitlements.Evaluation(true, now.plusSeconds(10), 4L);
    when(billing.evaluate(ACTOR, "PUBLISH_BOOKS")).thenReturn(own);
    when(billing.evaluate(HOLDER, "PREMIUM_FAMILY")).thenReturn(v[0].equals("expired_minor") ? own : premium);
    when(supervision.minorProduct(ACTOR, new KeycloakAgeFacts.Account(true, age), premium.effectiveUntil()))
        .thenReturn(new SupervisionStore.Facts(true, null, premium.effectiveUntil(), UUID.randomUUID(), 7L, null, null));
    when(supervision.minorProduct(ACTOR, new KeycloakAgeFacts.Account(true, age), null))
        .thenReturn(SupervisionStore.Facts.denied("ACCOUNT_RESTRICTED", now));
    var resource = new Resource(type, null, owner);
    SupervisionStore.Scope requested = scope;
    if (v[0].equals("decision_down")) {
      when(identity.read(ACTOR)).thenThrow(FamilyProblem.unavailable());
      assertEquals(503, assertThrows(FamilyProblem.class, () ->
          eval.evaluate(ACTOR, action, resource, null, null, requested)).getStatusCode().value());
      return;
    }
    var result = eval.evaluate(ACTOR, action, resource, null, null, requested);
    assertEquals(v[6].equals("—") ? null : ProductPolicy.Reason.valueOf(v[6]), result.reason());
    if (action == Action.SUPERVISE_HISTORY || action == Action.SUPERVISE_COMMENTS
        || action == Action.SESSION_VIEW || action == Action.INVITE_SELF || v[0].contains("midnight_18")) {
      verifyNoInteractions(billing);
    }
  }
}
