package com.bookrush.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookAnalyticsApplication {
  public static void main(String[] args) { SpringApplication.run(BookAnalyticsApplication.class, args); }
}
