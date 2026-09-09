package com.bookrush.ingestion;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
    title = "BookRush Book Ingestion API",
    version = "v1",
    description = "Operações administrativas de ingestão, staging e recuperação."))
public class OpenApiConfiguration {}
