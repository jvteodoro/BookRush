package com.bookrush.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
  @Bean OpenAPI analyticsOpenApi() {
    return new OpenAPI().info(new Info().title("BookRush Analytics API")
        .version("v1").description("Jobs e consultas de analytics ligados à versão textual exata."));
  }
}
