package com.bookrush.catalog;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
    title = "BookRush Catalog API",
    version = "v1",
    description = "Catálogo bibliográfico e distribuição de assets de livros."))
public class OpenApiConfiguration {}
