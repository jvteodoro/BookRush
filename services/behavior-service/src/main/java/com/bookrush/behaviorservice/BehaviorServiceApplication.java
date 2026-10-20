package com.bookrush.behaviorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BehaviorServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(BehaviorServiceApplication.class, args);
  }
}
