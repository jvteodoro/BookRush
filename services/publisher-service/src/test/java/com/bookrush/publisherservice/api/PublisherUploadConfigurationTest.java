package com.bookrush.publisherservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.bookrush.publisherservice.storage.StorageConfiguration.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class PublisherUploadConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      // Controller must register even when its dependencies are defined later.
      .withUserConfiguration(PublisherUploadController.class, Dependencies.class);
  @Test void enabledStorageRegistersTheUploadController() {
    context.withPropertyValues("publisher.storage.enabled=true")
        .run(c -> assertThat(c).hasSingleBean(PublisherUploadController.class));
  }
  @Test void disabledStorageDoesNotExposeUploadRoutes() {
    context.withPropertyValues("publisher.storage.enabled=false")
        .run(c -> assertThat(c).doesNotHaveBean(PublisherUploadController.class));
  }
  @Configuration(proxyBeanMethods = false)
  static class Dependencies {
    @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
    @Bean S3Client s3() { return mock(S3Client.class); }
    @Bean S3Presigner signer() { return mock(S3Presigner.class); }
    @Bean StorageProperties properties() { return new StorageProperties(); }
  }
}
