package com.bookrush.analytics;

import com.bookrush.analytics.config.AnalyticsRuntimeProperties;
import com.bookrush.analytics.config.AnalyticsV2GeneratorProperties;
import com.bookrush.analytics.storage.AnalyticsStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  AnalyticsStorageProperties.class,
  AnalyticsV2GeneratorProperties.class,
  AnalyticsRuntimeProperties.class
})
public class BookAnalyticsApplication {
  public static void main(String[] args) {
    SpringApplication.run(BookAnalyticsApplication.class, args);
  }
}
