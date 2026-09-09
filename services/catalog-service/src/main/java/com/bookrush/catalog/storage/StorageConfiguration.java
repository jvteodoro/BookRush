package com.bookrush.catalog.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
  @Configuration
  @ConditionalOnProperty(name="storage.enabled",havingValue="true")
  static class Enabled {
    @Bean AwsCredentialsProvider storageCredentials(StorageProperties p) {
      return p.accessKey()==null || p.accessKey().isBlank() ? DefaultCredentialsProvider.create()
          : StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(),p.secretKey()));
    }
    @Bean S3Configuration s3Options(StorageProperties p) {
      return S3Configuration.builder().pathStyleAccessEnabled(p.pathStyle()).chunkedEncodingEnabled(false).build();
    }
    @Bean(destroyMethod="close") S3Client s3Client(StorageProperties p, AwsCredentialsProvider credentials, S3Configuration options) {
      var builder=S3Client.builder().region(Region.of(p.region())).credentialsProvider(credentials)
          .serviceConfiguration(options)
          .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(p.timeout()))
          .overrideConfiguration(c->c.apiCallTimeout(p.timeout()).apiCallAttemptTimeout(p.timeout()));
      if(p.endpoint()!=null) builder.endpointOverride(p.endpoint());
      return builder.build();
    }
    @Bean(destroyMethod="close") S3Presigner s3Presigner(StorageProperties p, AwsCredentialsProvider credentials, S3Configuration options) {
      var builder=S3Presigner.builder().region(Region.of(p.region())).credentialsProvider(credentials).serviceConfiguration(options);
      if(p.publicEndpoint()!=null) builder.endpointOverride(p.publicEndpoint());
      else if(p.endpoint()!=null) builder.endpointOverride(p.endpoint());
      return builder.build();
    }
    @Bean BucketSelector bucketSelector(StorageProperties p) { return new BucketSelector(p.buckets()); }
    @Bean ObjectStorage objectStorage(S3Client client,S3Presigner signer,io.micrometer.core.instrument.MeterRegistry metrics) {
      return new S3ObjectStorage(client,signer,metrics);
    }
    @Bean org.springframework.boot.ApplicationRunner initializeBuckets(S3Client client,StorageProperties p) {
      return args -> {
        for(String bucket:p.buckets().all()) {
          try { client.headBucket(r->r.bucket(bucket)); }
          catch(software.amazon.awssdk.services.s3.model.S3Exception e) {
            if(e.statusCode()!=404) throw new StorageFailure(StorageFailure.Kind.UNAVAILABLE);
            try { client.createBucket(r->{
              r.bucket(bucket);
              if(!p.region().equals("us-east-1")) r.createBucketConfiguration(c->c.locationConstraint(p.region()));
            }); }
            catch(software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException ignored) {}
          }
        }
      };
    }
  }
}
