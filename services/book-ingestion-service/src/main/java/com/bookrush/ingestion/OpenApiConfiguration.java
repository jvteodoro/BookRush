package com.bookrush.ingestion;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "keycloak", type = SecuritySchemeType.OAUTH2, description = "Bearer JWT emitido pelo realm bookrush do Keycloak.", flows = @OAuthFlows(password = @OAuthFlow(tokenUrl = "https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush/protocol/openid-connect/token")))
@OpenAPIDefinition(info = @Info(
    title = "BookRush Book Ingestion API",
    version = "v1",
    description = "Operações administrativas de ingestão, staging e recuperação."))
public class OpenApiConfiguration {
  @Bean
  OpenAPI ingestionOpenApi(@Value("${ingestion.openapi.server-url:https://bookrush.jteodoro.tec.br/ingestion}") String serverUrl) {
    return new OpenAPI().servers(List.of(new Server().url(serverUrl).description("Gateway público BookRush /ingestion")));
  }
}
