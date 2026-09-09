package com.bookrush.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookIngestionApplication {
  public static void main(String[] args) {
    SpringApplication.run(BookIngestionApplication.class, args);
  }
}
