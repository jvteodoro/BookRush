package com.bookrush.catalog;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class StatusController {
  @GetMapping("/status")
  Map<String, String> status() {
    return Map.of("service", "catalog-service", "status", "ok");
  }
}
