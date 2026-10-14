package com.bookrush.catalog.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AssetSecurityCorsTest {
  @Test
  void exposesConfiguredOriginsForCatalogBrowserRoutes() {
    var source =
        new AssetSecurity()
            .catalogCorsConfigurationSource(
                new CatalogCorsProperties(
                    List.of(
                        "https://bookrush.jteodoro.tec.br",
                        "https://docs-bookrush.jteodoro.tec.br")));

    var internal = source.getCorsConfiguration(
        new MockHttpServletRequest("OPTIONS", "/api/internal/v1/catalog/books"));
    var admin = source.getCorsConfiguration(
        new MockHttpServletRequest("OPTIONS", "/api/admin/books/book/assets"));

    assertThat(internal).isNotNull();
    assertThat(admin).isNotNull();
    assertThat(admin.getAllowedOrigins())
        .containsExactly(
            "https://bookrush.jteodoro.tec.br", "https://docs-bookrush.jteodoro.tec.br");
    assertThat(admin.getAllowedMethods())
        .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    assertThat(admin.getAllowedHeaders())
        .containsExactly("Authorization", "Accept", "Content-Type", "Idempotency-Key", "X-Admin-Client");
    assertThat(admin.getAllowCredentials()).isFalse();
  }
}
