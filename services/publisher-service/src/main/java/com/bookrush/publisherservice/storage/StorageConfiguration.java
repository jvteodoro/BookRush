package com.bookrush.publisherservice.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name="publisher.storage.enabled", havingValue="true")
public class StorageConfiguration {
  @Bean @ConfigurationProperties("publisher.storage") StorageProperties storageProperties(){ return new StorageProperties(); }
  @Bean AwsCredentialsProvider storageCredentials(StorageProperties p){ return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey,p.secretKey)); }
  @Bean(destroyMethod="close") S3Client s3Client(StorageProperties p, AwsCredentialsProvider c){ var b=S3Client.builder().region(Region.of(p.region)).credentialsProvider(c).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))); if(p.endpoint!=null)b.endpointOverride(p.endpoint); return b.build(); }
  @Bean(destroyMethod="close") S3Presigner s3Presigner(StorageProperties p, AwsCredentialsProvider c){ var b=S3Presigner.builder().region(Region.of(p.region)).credentialsProvider(c); if(p.publicEndpoint!=null)b.endpointOverride(p.publicEndpoint); else if(p.endpoint!=null)b.endpointOverride(p.endpoint); return b.build(); }
  @Bean org.springframework.boot.ApplicationRunner initializeBucket(S3Client s3, StorageProperties p){ return args -> { try { s3.headBucket(r->r.bucket(p.bucket)); } catch (software.amazon.awssdk.services.s3.model.S3Exception e) { if(e.statusCode()==404) s3.createBucket(r->r.bucket(p.bucket)); else throw e; } }; }
  public static class StorageProperties { public boolean enabled; public URI endpoint; public URI publicEndpoint; public String region="us-east-1"; public String accessKey; public String secretKey; public String bucket="books-staging"; public int ttlSeconds=900; }
}
