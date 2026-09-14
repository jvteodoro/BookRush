package com.bookrush.analytics.storage;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "analytics.worker.enabled", havingValue = "true")
@EnableConfigurationProperties(AnalyticsStorageProperties.class)
public class AnalyticsStorageConfiguration {
  @Bean(destroyMethod = "close")
  S3Client analyticsS3Client(AnalyticsStorageProperties p) {
    return S3Client.builder()
        .endpointOverride(p.endpoint())
        .region(Region.of(p.region()))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey())))
        .serviceConfiguration(c -> c.pathStyleAccessEnabled(p.pathStyle()).chunkedEncodingEnabled(false))
        .httpClientBuilder(UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(Math.max(1, p.timeoutSeconds())))
            .socketTimeout(Duration.ofSeconds(Math.max(1, p.timeoutSeconds()))))
        .build();
  }
}
