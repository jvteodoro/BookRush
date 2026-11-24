package com.bookrush.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MockBillingEnabledTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withBean(org.springframework.jdbc.core.JdbcTemplate.class,
          () -> mock(org.springframework.jdbc.core.JdbcTemplate.class))
      .withBean(SubscriptionStore.class, () -> mock(SubscriptionStore.class))
      .withBean(BillingInbox.class, () -> mock(BillingInbox.class))
      .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
      .withPropertyValues("bookrush.security.issuer=https://test.invalid")
      .withUserConfiguration(MockBillingProvider.class, MockBillingController.class);

  @Test
  void defaultsToNoProviderOrControllerAndRequiresExplicitFlag() {
    context.withPropertyValues("spring.profiles.active=billing-mock", "bookrush.observability.environment=test")
        .run(app -> assertThat(app).doesNotHaveBean(MockBillingProvider.class).doesNotHaveBean(MockBillingController.class));
  }

  @Test
  void productionEnvironmentAndProductionProfilesVetoTheMock() {
    context.withPropertyValues("spring.profiles.active=billing-mock", "subscription.mock.enabled=true",
            "bookrush.observability.environment=production")
        .run(app -> assertThat(app).doesNotHaveBean(MockBillingProvider.class).doesNotHaveBean(MockBillingController.class));
    context.withPropertyValues("spring.profiles.active=billing-mock,prod", "subscription.mock.enabled=true",
            "bookrush.observability.environment=local")
        .run(app -> assertThat(app).doesNotHaveBean(MockBillingProvider.class).doesNotHaveBean(MockBillingController.class));
  }

  @Test
  void requiresTheMockProfileEvenInNonProduction() {
    var environment = new MockEnvironment().withProperty("subscription.mock.enabled", "true")
        .withProperty("bookrush.observability.environment", "local");
    assertThat(MockBillingEnabled.allowed(environment)).isFalse();
    environment.setActiveProfiles("billing-mock");
    assertThat(MockBillingEnabled.allowed(environment)).isTrue();
  }
}
