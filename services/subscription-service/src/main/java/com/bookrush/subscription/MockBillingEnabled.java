package com.bookrush.subscription;

import java.util.Set;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class MockBillingEnabled implements Condition {
  private static final Set<String> NON_PRODUCTION = Set.of("local", "development", "test");

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return allowed(context.getEnvironment());
  }

  static boolean allowed(Environment environment) {
    boolean declaredNonProduction =
        NON_PRODUCTION.contains(environment.getProperty("bookrush.observability.environment", "unknown"))
            && !environment.matchesProfiles("prod", "production");
    boolean productionAllowed = environment.getProperty("subscription.mock.production-allowed", Boolean.class, false);
    return environment.matchesProfiles("billing-mock")
        && environment.getProperty("subscription.mock.enabled", Boolean.class, false)
        && (declaredNonProduction || productionAllowed);
  }
}
