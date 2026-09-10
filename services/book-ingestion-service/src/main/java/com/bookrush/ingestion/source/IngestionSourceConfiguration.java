package com.bookrush.ingestion.source;

import com.bookrush.ingestion.config.IngestionProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionSourceConfiguration {
  @Bean
  HttpClient ingestionHttpClient(IngestionProperties properties) {
    return HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Bean
  SourceUrlPolicy sourceUrlPolicy(GutenbergProperties gutenberg) {
    var hosts = new java.util.HashSet<>(Set.of("www.gutenberg.org", "gutenberg.org"));
    hosts.add(java.net.URI.create(gutenberg.baseUrl()).getHost());
    return new SourceUrlPolicy(hosts, gutenberg.allowPrivateAddresses(), 3);
  }

  @Bean
  SnapshotDownloader snapshotDownloader(HttpClient client, SourceUrlPolicy policy, IngestionProperties properties) {
    return new SnapshotDownloader(client, policy, properties.maxRecordBytes());
  }
}
