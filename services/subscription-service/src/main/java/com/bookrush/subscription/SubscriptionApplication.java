package com.bookrush.subscription;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SubscriptionApplication {
  public static void main(String[] args) {
    SpringApplication.run(SubscriptionApplication.class, args);
  }

  @Bean
  Clock subscriptionClock() {
    return Clock.systemUTC();
  }
}
