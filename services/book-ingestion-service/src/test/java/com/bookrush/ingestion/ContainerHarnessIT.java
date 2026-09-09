package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ContainerHarnessIT {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static final GenericContainer<?> seaweed = new GenericContainer<>("bookrush/seaweedfs:4.46")
      .withExposedPorts(8333);

  @Test
  void realPostgresAndDuckdbAreAvailable() throws Exception {
    assertTrue(postgres.isRunning());
    try (var connection = DriverManager.getConnection("jdbc:duckdb:");
         var statement = connection.createStatement();
         var result = statement.executeQuery("select 42")) {
      assertTrue(result.next());
    }
  }
}
