package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers
class ContainerHarnessIT {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static final GenericContainer<?> seaweed = new GenericContainer<>("chrislusf/seaweedfs:4.46")
      .withCommand("server", "-dir=/data", "-s3")
      .withExposedPorts(9333, 8333)
      .waitingFor(Wait.forHttp("/cluster/status").forPort(9333).forStatusCode(200));

  @Test
  void realPostgresAndDuckdbAreAvailable() throws Exception {
    assertTrue(postgres.isRunning());
    try (var connection = DriverManager.getConnection("jdbc:duckdb:");
         var statement = connection.createStatement();
         var result = statement.executeQuery("select 42")) {
      assertTrue(result.next());
    }
    var fixture = getClass().getResourceAsStream("/fixtures/gutenberg/catalog.rdf");
    assertTrue(fixture != null);
    var rdf = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(rdf.contains("ebooks/1342"));
    var health = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://" + seaweed.getHost() + ":" + seaweed.getMappedPort(9333) + "/cluster/status")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertTrue(health.statusCode() == 200);
  }
}
