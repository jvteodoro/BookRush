package com.bookrush.ingestion.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(RawStorageProperties.class)
public class RawStorageConfiguration {
  @Bean(destroyMethod = "close")
  S3Client rawS3Client(RawStorageProperties p) {
    return S3Client.builder().endpointOverride(p.endpoint()).region(Region.of(p.region()))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey())))
        .serviceConfiguration(c -> c.pathStyleAccessEnabled(p.pathStyle()).chunkedEncodingEnabled(false))
        .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(p.timeout()))
        .build();
  }
}
