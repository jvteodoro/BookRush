package com.bookrush.family;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FamilyApplication {
  public static void main(String[] args) {
    SpringApplication.run(FamilyApplication.class, args);
  }

  @Bean
  Clock familyClock() {
    return Clock.systemUTC();
  }
}
