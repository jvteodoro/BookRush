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
  @Bean AwsCredentialsProvider storageCredentials(StorageProperties p){ return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(),p.getSecretKey())); }
  private software.amazon.awssdk.services.s3.S3Configuration options(){ return software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build(); }
  @Bean(destroyMethod="close") S3Client s3Client(StorageProperties p, AwsCredentialsProvider c){ var b=S3Client.builder().region(Region.of(p.getRegion())).credentialsProvider(c).serviceConfiguration(options()).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))); if(p.getEndpoint()!=null)b.endpointOverride(p.getEndpoint()); return b.build(); }
  @Bean(destroyMethod="close") S3Presigner s3Presigner(StorageProperties p, AwsCredentialsProvider c){ var b=S3Presigner.builder().region(Region.of(p.getRegion())).credentialsProvider(c).serviceConfiguration(options()); if(p.getPublicEndpoint()!=null)b.endpointOverride(p.getPublicEndpoint()); else if(p.getEndpoint()!=null)b.endpointOverride(p.getEndpoint()); return b.build(); }
  @Bean org.springframework.boot.ApplicationRunner initializeBucket(S3Client s3, StorageProperties p){ return args -> { try { s3.headBucket(r->r.bucket(p.getBucket())); } catch (software.amazon.awssdk.services.s3.model.S3Exception e) { if(e.statusCode()==404) s3.createBucket(r->r.bucket(p.getBucket())); else throw e; } }; }
  public static class StorageProperties { private boolean enabled; private URI endpoint; private URI publicEndpoint; private String region="us-east-1"; private String accessKey; private String secretKey; private String bucket="books-staging"; private int ttlSeconds=900;
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public URI getEndpoint(){return endpoint;} public void setEndpoint(URI v){endpoint=v;} public URI getPublicEndpoint(){return publicEndpoint;} public void setPublicEndpoint(URI v){publicEndpoint=v;} public String getRegion(){return region;} public void setRegion(String v){region=v;} public String getAccessKey(){return accessKey;} public void setAccessKey(String v){accessKey=v;} public String getSecretKey(){return secretKey;} public void setSecretKey(String v){secretKey=v;} public String getBucket(){return bucket;} public void setBucket(String v){bucket=v;} public int getTtlSeconds(){return ttlSeconds;} public void setTtlSeconds(int v){ttlSeconds=v;}
  }
}
