package com.bookrush.catalog;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "assetAdmin", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "opaque", in = SecuritySchemeIn.HEADER, description = "Token ASSET_ADMIN_TOKEN para operações administrativas de assets.")
@SecurityScheme(name = "catalogService", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "opaque", in = SecuritySchemeIn.HEADER, description = "Token interno usado pelo book-ingestion-service.")
@OpenAPIDefinition(info = @Info(
    title = "BookRush Catalog API",
    version = "v1",
    description = "Catálogo bibliográfico e distribuição de assets de livros."))
public class OpenApiConfiguration {
  @Bean
  OpenAPI catalogOpenApi(@Value("${bookrush.openapi.server-url:https://bookrush.jteodoro.tec.br}") String serverUrl) {
    return new OpenAPI().servers(List.of(new Server().url(serverUrl).description("Gateway público BookRush")));
  }
}
