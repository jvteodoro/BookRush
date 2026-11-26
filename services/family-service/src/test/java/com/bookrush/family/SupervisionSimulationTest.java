package com.bookrush.family;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class SupervisionSimulationTest {
  @Test
  void requiresExplicitOptInAndProductionCannotOverride() {
    var env = new MockEnvironment();
    assertFalse(SupervisionController.simulationEnabled(env));
    env.withProperty("family.supervision-simulation", "true")
        .withProperty("bookrush.observability.environment", "test");
    assertFalse(SupervisionController.simulationEnabled(env));
    env.setActiveProfiles("supervision-mock");
    assertTrue(SupervisionController.simulationEnabled(env));
    env.setActiveProfiles("supervision-mock", "production");
    assertFalse(SupervisionController.simulationEnabled(env));
    env.setActiveProfiles("supervision-mock");
    env.setProperty("bookrush.observability.environment", "production");
    assertFalse(SupervisionController.simulationEnabled(env));
  }
}
