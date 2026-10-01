package com.bookrush.recommendationservice.api;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
  private final RestClient catalog;
  public RecommendationController(@Value("${bookrush.catalog-url:http://catalog-service:8080}") String url) { this.catalog=RestClient.builder().baseUrl(url).build(); }
  @GetMapping("/feed")
  public Map<String,Object> feed(@RequestParam(defaultValue="20") int size) {
    int limit=Math.max(1,Math.min(size,100));
    Map<?,?> result=catalog.get().uri("/api/v1/books?page=0&size="+limit).retrieve().body(Map.class);
    List<?> books=result!=null && result.get("items") instanceof List<?> l ? l : List.of();
    String requestId=UUID.randomUUID().toString();
    List<Map<String,Object>> items=new ArrayList<>(); int rank=1;
    for(Object b:books){ items.add(Map.of("book",b,"recommendationRequestId",requestId,"impressionId",UUID.randomUUID().toString(),"modelVersion","heuristic-v1","rank",rank++)); }
    return Map.of("requestId",requestId,"modelVersion","heuristic-v1","generatedAt",Instant.now(),"items",items);
  }
}
